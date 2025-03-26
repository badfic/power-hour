package com.badfic.powerhour;

import com.badfic.powerhour.commands.BaseSlashCommand;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessageListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final PowerHourConfiguration powerHourConfiguration;
    private final List<BaseSlashCommand> slashCommands;

    public MessageListener(final PowerHourConfiguration powerHourConfiguration, final List<BaseSlashCommand> slashCommands) {
        this.powerHourConfiguration = powerHourConfiguration;
        this.slashCommands = slashCommands;
    }

    @Override
    public void onReady(@NotNull final ReadyEvent event) {
        log.info("Received ready event for [user={}]", event.getJDA().getSelfUser());

        final var guilds = event.getJDA().getGuilds();

        for (final var guild : guilds) {
            var commandListUpdateAction = guild.updateCommands();

            for (final var slashCommand : slashCommands) {
                if (CollectionUtils.isNotEmpty(slashCommand.getOptions())) {
                    commandListUpdateAction = commandListUpdateAction.addCommands(Commands.slash(slashCommand.getName(), slashCommand.getHelp())
                            .addOptions(slashCommand.getOptions()));
                } else {
                    commandListUpdateAction = commandListUpdateAction.addCommands(Commands.slash(slashCommand.getName(), slashCommand.getHelp()));
                }
            }

            commandListUpdateAction.submit().whenComplete((success, err) -> {
                if (err != null) {
                    log.error("Failed to upsert slash command(s) {}", success.stream().map(Command::getName).collect(Collectors.joining(", ")), err);
                    return;
                }

                log.info("Successfully upserted slash command(s) {}", success.stream().map(Command::getName).collect(Collectors.joining(", ")));
            });
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull final SlashCommandInteractionEvent event) {
        for (final var slashCommand : slashCommands) {
            if (event.getName().equals(slashCommand.getName())) {
                // no guildOnly check because all slash commands are guild only

                // owner commands
                if (slashCommand.isOwnerCommand() && !event.getUser().getId().equals(powerHourConfiguration.ownerId)) {
                    event.reply("This is an owner command, only Santiago can execute it").queue();
                    return;
                }

                // required role check
                if (Objects.nonNull(slashCommand.getRequiredRole())) {
                    final var requiredRole = slashCommand.getRequiredRole();

                    if (Objects.isNull(event.getMember()) || !slashCommand.hasRole(event.getMember(), requiredRole)) {
                        event.reply(String.format("You must have %s role to execute this command", requiredRole)).queue();
                        return;
                    }
                }

                // nsfw check
                if (slashCommand.isNsfwOnly()) {
                    if (!(event.getChannel() instanceof IAgeRestrictedChannel ageRestrictedChannel) || !ageRestrictedChannel.isNSFW()) {
                        event.reply("This is an nsfw only command, it cannot be executed in this channel").queue();
                        return;
                    }
                }

                slashCommand.execute(event);
                return;
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull final CommandAutoCompleteInteractionEvent event) {
        for (final var slashCommand : slashCommands) {
            if (event.getName().equals(slashCommand.getName())) {
                slashCommand.onAutoComplete(event);
                return;
            }
        }
    }
}
