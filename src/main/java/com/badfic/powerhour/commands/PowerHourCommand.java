package com.badfic.powerhour.commands;

import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Service;

@Service
public class PowerHourCommand extends BaseSlashCommand {
    private static final Command.Choice[] COMMAND_CHOICES = {
        new Command.Choice("HELP", "HELP"),
        new Command.Choice("START", "START"),
        new Command.Choice("PAUSE", "PAUSE"),
        new Command.Choice("RESUME", "RESUME"),
        new Command.Choice("STATUS", "STATUS"),
        new Command.Choice("STOP", "STOP")
    };

    private final AudioPlayerManager audioPlayerManager;
    private final Map<Long, AudioPlayer> guildPlayerMap;
    private final ReadWriteLock guildPlayerLock;

    public PowerHourCommand(final AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        this.guildPlayerMap = new HashMap<>();
        this.guildPlayerLock = new ReentrantReadWriteLock();
        name = "powerhour";
        help = "Help, Start, Pause, Resume, Get the Status or Stop a Power Hour in your current voice channel";
        options = List.of(new OptionData(OptionType.STRING, "action", "Help, Start, Pause, Resume, Status, or Stop", true, true));
    }

    @Override
    public void execute(final SlashCommandInteractionEvent event) {
        final var interactionHook = event.deferReply().submit();

        final var guild = event.getGuild();
        if (guild == null) {
            replyToInteractionHook(event, interactionHook, "❌ You must be in a discord server voice channel to use this command. [errorCode=11]");
            return;
        }

        final var member = event.getMember();
        if (member == null) {
            replyToInteractionHook(event, interactionHook, "❌ You must be in a discord server voice channel to use this command. [errorCode=10]");
            return;
        }

        final var action = event.getOption("action");
        if (action == null) {
            replyToInteractionHook(event, interactionHook, "❓ You must pick an action");
            return;
        }

        final var actionName = action.getAsString().toUpperCase().strip();

        switch (actionName) {
            case "HELP" -> {
                final var description = "Power Hour is a drinking game that involves drinking once every minute for an hour.\n" +
                        "The Power Hour bot facilitates starting an audio reminder that will play every one minute in a voice channel that you are connected to.\n\n" +
                        "* /powerhour START to start a power hour in the voice channel you are actively connected to.\n" +
                        "* /powerhour PAUSE to pause an actively running power hour.\n" +
                        "* /powerhour RESUME to resume a paused power hour. It will pick up at the same minute it left off at.\n" +
                        "* /powerhour STATUS to find out how much time is left in a running power hour.\n" +
                        "* /powerhour STOP to stop the power hour.";
                final var embed = new MessageEmbed(null, "/powerhour HELP", description, EmbedType.RICH, OffsetDateTime.now(), 0, null, null, null, null, null, null, null);
                replyToInteractionHook(event, interactionHook, embed);
                return;
            }
            case "STATUS" -> {
                final var audioPlayer = getGuildAudioPlayer(guild);
                if (audioPlayer == null) {
                    replyToInteractionHook(event, interactionHook, "❓ There is no active Power Hour. Try /powerhour START instead.");
                    return;
                }

                final var track = audioPlayer.getPlayingTrack();
                if (track == null) {
                    replyToInteractionHook(event, interactionHook, "❓ There is no active Power Hour. Try /powerhour START instead.");
                    return;
                }

                final var positionMinutes = TimeUnit.MILLISECONDS.toMinutes(track.getPosition());
                final var durationMinutes = TimeUnit.MILLISECONDS.toMinutes(track.getDuration());

                replyToInteractionHook(event, interactionHook, "The Power Hour is at %s out of %s minutes.".formatted(positionMinutes, durationMinutes));
                return;
            }
            case "START" -> {
                final var audioPlayer = getGuildAudioPlayer(guild);
                if (audioPlayer != null) {
                    replyToInteractionHook(event, interactionHook, "❌ There is already a Power Hour running. Only one Power Hour can run at a time.");
                    return;
                }
            }
            case "PAUSE" -> {
                final var audioPlayer = getGuildAudioPlayer(guild);
                if (audioPlayer == null) {
                    replyToInteractionHook(event, interactionHook, "❌ There is no active Power Hour to pause. Try /powerhour START instead.");
                    return;
                }

                audioPlayer.setPaused(true);
                replyToInteractionHook(event, interactionHook, "⏸️ Power Hour has been paused. Use /powerhour RESUME to resume.");
                return;
            }
            case "RESUME" -> {
                final var audioPlayer = getGuildAudioPlayer(guild);
                if (audioPlayer == null) {
                    replyToInteractionHook(event, interactionHook, "❌ There is no active Power Hour to resume. Try /powerhour START instead.");
                    return;
                }

                audioPlayer.setPaused(false);
                replyToInteractionHook(event, interactionHook, "▶️ Power Hour has been resumed. Use /powerhour PAUSE to pause again.");
                return;
            }
            case "STOP" -> {
                guildPlayerLock.writeLock().lock();
                try {
                    guildPlayerMap.remove(guild.getIdLong());
                } finally {
                    guildPlayerLock.writeLock().unlock();
                }

                guild.getAudioManager().closeAudioConnection();
                replyToInteractionHook(event, interactionHook, "⏹️ Power Hour has been stopped. Use /powerhour START to start over.");
                return;
            }
        }

        final var voiceState = member.getVoiceState();
        if (voiceState == null) {
            replyToInteractionHook(event, interactionHook, "❌ Please join a voice channel before using this command. [errorCode=12]");
            return;
        }

        final var channel = voiceState.getChannel();
        if (channel == null || channel.getType() != ChannelType.VOICE) {
            replyToInteractionHook(event, interactionHook, "❌ Please join a voice channel before using this command. [errorCode=13]");
            return;
        }

        final var voiceChannel = channel.asVoiceChannel();

        if ("START".equals(actionName)) {
            final var track = new OggAudioTrack(new AudioTrackInfo("Power Hour", "", 3_605_310L, "", false, ""),
                    new NonSeekableInputStream(PowerHourCommand.class.getClassLoader().getResourceAsStream("power-hour.opus")));

            connectToVoiceChannelAndPlay(event, interactionHook, voiceChannel, track);
            replyToInteractionHook(event, interactionHook, "✅ Power Hour has started");
        } else {
            replyToInteractionHook(event, interactionHook, "❌ Unrecognized Power Hour command `%s`".formatted(actionName));
        }
    }

    @Override
    public void onAutoComplete(final CommandAutoCompleteInteractionEvent event) {
        event.replyChoices(COMMAND_CHOICES).queue();
    }

    private AudioPlayer getGuildAudioPlayer(final Guild guild) {
        guildPlayerLock.readLock().lock();
        try {
            return guildPlayerMap.get(guild.getIdLong());
        } finally {
            guildPlayerLock.readLock().unlock();
        }
    }

    private void connectToVoiceChannelAndPlay(final SlashCommandInteractionEvent event, final CompletableFuture<InteractionHook> interactionHook,
                                              final VoiceChannel voiceChannel, final AudioTrack audioTrack) {
        final var audioManager = voiceChannel.getGuild().getAudioManager();

        audioManager.closeAudioConnection();

        try {
            audioManager.openAudioConnection(voiceChannel);
        } catch (UnsupportedOperationException | InsufficientPermissionException e) {
            replyToInteractionHook(event, interactionHook, "❌ I don't have permission to access the voice channel you are currently in.");
            return;
        }

        audioManager.setSelfDeafened(true);

        final var audioPlayer = audioPlayerManager.createPlayer();
        audioManager.setSendingHandler(new AudioPlayerSendHandler(audioPlayer));

        guildPlayerLock.writeLock().lock();
        try {
            guildPlayerMap.put(voiceChannel.getGuild().getIdLong(), audioPlayer);
        } finally {
            guildPlayerLock.writeLock().unlock();
        }

        audioPlayer.startTrack(audioTrack, false);
    }
}
