package dev.omatheusmesmo.qlawkus.routine;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Builds what the agent is asked when a routine is due: a fixed header with the date, the owner's
 * instruction or the built-in one, and a fixed reply contract. The header and the contract are not
 * replaceable, so an owner prompt cannot make a routine skip saying what it could not read.
 */
final class RoutinePrompts {

    private static final String WEEKLY = """
            Write the owner's report of this week. Collect what they did since Monday: the pull requests \
            they merged and the commits they pushed (use the shell tool with the gh CLI, for example \
            gh search prs --author @me --merged and gh api for their commits), and the achievements already \
            recorded in the brag document (use the brag tool). Group it by project and keep it short. \
            Finish by offering to record the items worth keeping as brag achievements. Do not record anything \
            until the owner answers.""";

    private static final String MORNING = """
            Write the owner's briefing for this week. List this week's meetings from the Google Calendar \
            tools, day by day. Then list the open pull requests that look abandoned (use the shell tool \
            with the gh CLI), saying for each one how long it has been quiet.""";

    private static final String CONTRACT = """
            Reply with the message to send the owner, as plain chat text. If a tool is unavailable or \
            fails, or you cannot read a source, still send the rest and say plainly what you could not read. \
            Do not retry a failing tool more than once.""";

    private RoutinePrompts() {
    }

    static String build(Routine routine, ZonedDateTime now, Optional<String> custom, int abandonedAfterDays) {
        String instruction = custom.filter(text -> !text.isBlank()).map(String::strip)
                .orElse(routine == Routine.WEEKLY ? WEEKLY : MORNING);
        StringBuilder prompt = new StringBuilder("The owner's ").append(routine.title())
                .append(" routine is due now (it is ")
                .append(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append(").\n")
                .append(instruction).append('\n');
        if (routine == Routine.MORNING) {
            prompt.append("A pull request counts as abandoned after ").append(abandonedAfterDays)
                    .append(" days without activity.\n");
        }
        return prompt.append(CONTRACT).toString();
    }
}
