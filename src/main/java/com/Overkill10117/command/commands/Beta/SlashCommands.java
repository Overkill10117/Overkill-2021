package com.Overkill10117.command.commands.Beta;

import Requests.OpenTDB;
import com.Overkill10117.Config;
import com.Overkill10117.command.CommandContext;
import com.Overkill10117.command.commands.Fun.TriviaCommand;
import com.Overkill10117.command.commands.Utils.UtilNum;
import com.fasterxml.jackson.databind.JsonNode;
import me.duncte123.botcommons.messaging.EmbedUtils;
import me.duncte123.botcommons.web.WebUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class  SlashCommands extends ListenerAdapter {
    public static HashMap<User, String> storeAnswer = new HashMap<>();
    public static HashMap<User, String> storeQuestion = new HashMap<>();
    public static HashMap<User, String> storeDifficulty = new HashMap<>();
    private Object CommandContext;

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // Only accept commands from guilds
        if (event.getGuild() == null)
            return;
        switch (event.getName()) {
            case "ban":
                Member member = event.getOption("user").getAsMember(); // the "user" option is required so it doesn't need a null-check here
                User user = event.getOption("user").getAsUser();
                ban(event, user, member);
                break;
            case "say":
                say(event, Objects.requireNonNull(event.getOption("content")).getAsString()); // content is required so no null-check here
                break;
            case "leave":
                leave(event);
                break;
            case "prune": // 2 stage command with a button prompt
                prune(event);
                break;
            case "spam":
                spam(event);
                break;
            case "meme":
                meme(event);
                break;
            case "trivia":
                trivia(event);
                break;
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonClick(ButtonClickEvent event) {
        // users can spoof this id so be careful what you do with this
        String[] id = event.getComponentId().split(":"); // this is the custom id we specified in our button
        String authorId = id[0];
        String type = id[1];
        // When storing state like this is it is highly recommended to do some kind of verification that it was generated by you, for instance a signature or local cache
        if (!authorId.equals(event.getUser().getId()))
            return;
        event.deferEdit().queue(); // acknowledge the button was clicked, otherwise the interaction will fail

        MessageChannel channel = event.getChannel();
        switch (type) {
            case "prune":
                int amount = Integer.parseInt(id[2]);
                event.getChannel().getIterableHistory()
                        .skipTo(event.getMessageIdLong())
                        .takeAsync(amount)
                        .thenAccept(channel::purgeMessages);
                // fallthrough delete the prompt message with our buttons
            case "delete":
                event.getHook().deleteOriginal().queue();
        }
    }

    public void ban(SlashCommandEvent event, User user, Member member) {
        event.deferReply(true).queue(); // Let the user know we received the command before doing anything else
        InteractionHook hook = event.getHook(); // This is a special webhook that allows you to send messages without having permissions in the channel and also allows ephemeral messages
        hook.setEphemeral(true); // All messages here will now be ephemeral implicitly
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage("You do not have the required permissions to ban users from this server.").queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage("I don't have the required permissions to ban users from this server.").queue();
            return;
        }

        if (member != null && !selfMember.canInteract(member)) {
            hook.sendMessage("This user is too powerful for me to ban.").queue();
            return;
        }

        int delDays = 0;
        OptionMapping option = event.getOption("del_days");
        if (option != null) // null = not provided
            delDays = (int) Math.max(0, Math.min(7, option.getAsLong()));
        // Ban the user and send a success response
        event.getGuild().ban(user, delDays)
                .flatMap(v -> hook.sendMessage("Banned user " + user.getAsTag()))
                .queue();
    }

    public void say(SlashCommandEvent event, String content) {
        event.reply(content).queue(); // This requires no permissions!
        if (content.isEmpty()) {
            event.reply("What will I say??").queue();
            event.reply("Usage: /say [word]").queue();
        }
    }

    public void leave(SlashCommandEvent event) {
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS))
            event.reply("You do not have permissions to kick me.").setEphemeral(true).queue();
        else
            event.reply("Leaving the server... :wave:") // Yep we received it
                    .flatMap(v -> event.getGuild().leave()) // Leave server after acknowledging the command
                    .queue();
    }

    public void prune(SlashCommandEvent event) {
        OptionMapping amountOption = event.getOption("amount"); // This is configured to be optional so check for null
        int amount = amountOption == null
                ? 100 // default 100
                : (int) Math.min(200, Math.max(2, amountOption.getAsLong())); // enforcement: must be between 2-200
        String userId = event.getUser().getId();
        event.reply("This will delete " + amount + " messages.\nAre you sure?") // prompt the user with a button menu
                .addActionRow(// this means "<style>(<id>, <label>)" the id can be spoofed by the user so setup some kinda verification system
                        Button.secondary(userId + ":delete", "Nevermind!"),
                        Button.danger(userId + ":prune:" + amount, "Yes!")) // the first parameter is the component id we use in onButtonClick above
                .queue();
    }

    public void spam(SlashCommandEvent event) {// This is configured to be optional so check for null
        final long guildID = event.getGuild().getIdLong();
        String prefix = Config.get("prefix");

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Spam");
        embedBuilder.setColor(Color.cyan);
        embedBuilder.setDescription("click the button to spam");

        event.replyEmbeds(embedBuilder.build()).addActionRows(
                ActionRow.of(
                        Button.secondary(event.getMember().getUser().getId() + ":spam", "Spam"))).queue();

        return;
    }

    public void meme(SlashCommandEvent event) {
        WebUtils.ins.getJSONObject("https://apis.duncte123.me/meme").async((json) -> {
            if (!json.get("success").asBoolean()) {
                event.reply("Something went wrong, try again later").queue();
                System.out.println(json);
                return;
            }

            final JsonNode data = json.get("data");
            final String title = data.get("title").asText();
            final String url = data.get("url").asText();
            final String image = data.get("image").asText();
            final EmbedBuilder embed = EmbedUtils.embedImageWithTitle(title, url, image);

            event.replyEmbeds(embed.build()).queue();
        });
    }

    public void trivia(SlashCommandEvent event) {
        OpenTDB obj = new OpenTDB();

        obj.getTrivia();

        System.out.println(obj.getQuestion());
        System.out.println(obj.getCorrectAnswer());

        String[] incorrectAnswers = obj.incorrectAnswers;
        for (int i = 0; i < obj.incorrectAnswers.length ; i++) {
            System.out.println(obj.incorrectAnswers[i]);
        }


        SelectionMenu.Builder menu = SelectionMenu.create("menu:class")
                .setPlaceholder("Choose the correct answer") // shows the placeholder indicating what this menu is for
                .setRequiredRange(1, 1);

        int x = 0;
        ArrayList<String> arrayList = new ArrayList<>();

        while (x < incorrectAnswers.length) {
            arrayList.add(incorrectAnswers[x]);
            x++;
        }

        x = 0;

        arrayList.add(obj.getCorrectAnswer());
        int size = arrayList.size();
        while (x < size) {
            int random = UtilNum.randomNum(0, size-1 - (x));
            String choice = arrayList.get(random).replace("&quot;", "'").replace("&#039;", "'").replace("&Uuml;", "ü").replace("&amp;", "&");
            System.out.println(choice);

            menu.addOption(choice, choice);
            arrayList.remove(choice);

            x++;
        }

        String msg = obj.getQuestion().replace("&quot;", "'").replace("&#039;", "'").replace("&Uuml;", "ü").replace("&amp;", "&");
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Trivia!!!");
        embedBuilder.addField("Category: ", obj.getCategory(), true);
        embedBuilder.addField("Difficulty: ", obj.getDifficulty(), true);
        embedBuilder.addField("Question: ", event.getUser().getAsMention() + " " + msg, false);
        embedBuilder.setColor(Color.cyan);
        event.getChannel().sendMessageEmbeds(embedBuilder.build()).setActionRow(menu.build()).queue();
        storeQuestion.put(event.getUser(), msg);
        storeDifficulty.put(event.getUser(), obj.getDifficulty());
        storeAnswer.put(event.getUser(), obj.getCorrectAnswer().replace("&quot;", "'").replace("&#039;", "'").replace("&Uuml;", "ü").replace("&amp;", "&"));
    }
}
