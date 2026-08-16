package com.furkan.apidebugagent.sqllog;

import java.util.List;

/**
 * The log lines one analysis got, and whether that is all of them.
 *
 * <p>The target returns rows ordered by {@code timestamp} ascending, so a full window is cut at the
 * end: the last requests in the range are missing and the one sitting on the boundary arrives half
 * finished. Repeat counts measured on such a window are lower than what really ran, which is why
 * {@code truncated} travels with the lines instead of being left as a log line nobody reads.
 *
 * @param truncated the target returned as many rows as it was allowed to, so there are probably
 *                  more
 * @param limit     the limit that was actually sent, after clamping
 */
public record LogWindow(List<LogLine> lines, boolean truncated, int limit) {
}
