package com.badfic.powerhour.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

public abstract class BaseSlashCommand {

    protected String name;
    protected String help;
    protected String requiredRole;
    protected boolean nsfwOnly;
    protected boolean ownerCommand;
    protected List<OptionData> options;

    public void onAutoComplete(final CommandAutoCompleteInteractionEvent event) {
        // do nothing
    }

    public abstract void execute(final SlashCommandInteractionEvent event);

    protected CompletableFuture<InteractionHook> replyToInteractionHook(final SlashCommandInteractionEvent event,
                                                                        final CompletableFuture<InteractionHook> interactionHook,
                                                                        final MessageEmbed messageEmbed) {
        return interactionHook.whenComplete((hook, err) -> {
            if (err != null) {
                event.getChannel().sendMessageEmbeds(messageEmbed).queue();
                return;
            }

            hook.editOriginalEmbeds(messageEmbed).queue();
        });
    }

    protected CompletableFuture<InteractionHook> replyToInteractionHook(final SlashCommandInteractionEvent event,
                                                                        final CompletableFuture<InteractionHook> interactionHook,
                                                                        final String message) {
        return interactionHook.whenComplete((hook, err) -> {
            if (err != null) {
                event.getChannel().sendMessage(message).queue();
                return;
            }

            hook.editOriginal(message).queue();
        });
    }

    protected CompletableFuture<InteractionHook> replyToInteractionHook(final SlashCommandInteractionEvent event,
                                                                        final CompletableFuture<InteractionHook> interactionHook,
                                                                        final FileUpload file) {
        return interactionHook.whenComplete((hook, err) -> {
            if (err != null) {
                event.getChannel().sendMessage(" ").addFiles(file).queue();
                return;
            }

            hook.editOriginalAttachments(file).queue();
        });
    }

    public boolean hasRole(final Member member, final String role) {
        return member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase(role));
    }

    public String getName() {
        return name;
    }

    public String getHelp() {
        return help;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public boolean isNsfwOnly() {
        return nsfwOnly;
    }

    public boolean isOwnerCommand() {
        return ownerCommand;
    }

    public List<OptionData> getOptions() {
        return options;
    }
}
