(function () {
  var h = SM.ui.h;

  function statBlock(label, value) {
    return h("div", { class: "stat-block" }, [
      h("div", { class: "stat-block__value", text: String(value) }),
      h("div", { class: "stat-block__label", text: label })
    ]);
  }

  function clockDuration(seconds) {
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
  }

  function startOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  function heatmapCell(date, count) {
    var cell = h("div", { class: "heatmap__cell" });
    if (date.getTime() > startOfDay(new Date()).getTime()) {
      cell.style.background = "transparent";
      return cell;
    }
    if (count <= 0) cell.style.background = "rgba(28, 38, 51, 0.35)";
    else if (count === 1) cell.style.background = "rgba(47, 128, 237, 0.45)";
    else if (count === 2) cell.style.background = "rgba(47, 128, 237, 0.7)";
    else cell.style.background = "#2F80ED";
    return cell;
  }

  function heatmap(perDay) {
    var monday = SM.startOfWeek(SM.today());
    var columns = [];
    for (var offset = 11; offset >= 0; offset -= 1) {
      var weekStart = SM.addDays(monday, -7 * offset);
      var cells = [];
      for (var index = 0; index < 7; index += 1) {
        var date = SM.addDays(weekStart, index);
        cells.push(heatmapCell(date, perDay[SM.iso(date)] || 0));
      }
      columns.push(h("div", { class: "heatmap__col" }, cells));
    }
    return h("div", { class: "heatmap" }, columns);
  }

  function weeklyBars(counts) {
    var max = counts.reduce(function (top, row) { return Math.max(top, row.count); }, 0) || 1;
    var bars = counts.map(function (row) {
      return h("div", { class: "bar" }, [
        h("div", {
          class: "bar__fill",
          style: {
            height: (4 + (56 * row.count) / max) + "px",
            background: row.count === 0 ? "rgba(47, 128, 237, 0.2)" : "rgba(47, 128, 237, 0.85)"
          }
        }),
        h("div", { class: "bar__value", text: String(row.count) })
      ]);
    });
    return h("div", { class: "bars" }, bars);
  }

  SM.route("stats", function (container) {
    var sessions = SM.completedSessions().slice().sort(function (a, b) {
      return (b.finishedAt || b.startedAt) - (a.finishedAt || a.startedAt);
    });
    var streak = SM.state.streak;
    var totalSets = SM.state.setLogs.length;
    var totalReps = SM.state.setLogs.reduce(function (sum, log) { return sum + (log.repsCompleted || 0); }, 0);
    var totalDuration = sessions.reduce(function (sum, session) { return sum + (session.durationSeconds || 0); }, 0);
    var plannedDays = SM.planSnapshot().trainingDays.length;

    var perDay = {};
    sessions.forEach(function (session) {
      var key = SM.iso(new Date(session.finishedAt || session.startedAt));
      perDay[key] = (perDay[key] || 0) + 1;
    });

    var monday = SM.startOfWeek(SM.today());
    var weekly = [];
    for (var offset = 7; offset >= 0; offset -= 1) {
      var weekStart = SM.addDays(monday, -7 * offset);
      var count = sessions.filter(function (session) {
        var date = SM.iso(new Date(session.finishedAt || session.startedAt));
        return date >= SM.iso(weekStart) && date <= SM.iso(SM.addDays(weekStart, 6));
      }).length;
      weekly.push({ weekStart: weekStart, count: count });
    }

    var recent = sessions.slice(0, 8).map(function (session) {
      return h("div", { class: "row--between" }, [
        h("span", { class: "body", text: SM.formatShort(new Date(session.finishedAt || session.startedAt)) }),
        h("span", { class: "small muted", text: SM.formatDuration(session.durationSeconds || 0) })
      ]);
    });

    container.appendChild(h("div", { class: "screen stats-page" }, [
      SM.ui.screenHead({ title: "Stats", back: function () { SM.back(); } }),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Streak" }),
        h("div", { class: "stat-row" }, [
          statBlock("Current", streak.currentStreak),
          statBlock("Longest", streak.longestStreak),
          statBlock("Freezes", streak.freezeTokens)
        ])
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Totals" }),
        h("div", { class: "stat-row" }, [
          statBlock("Workouts", sessions.length),
          statBlock("Sets", totalSets),
          statBlock("Reps", totalReps)
        ]),
        h("p", { class: "small muted", text: clockDuration(totalDuration) + " · " + plannedDays + " days/week" })
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Last 12 weeks" }),
        heatmap(perDay)
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Workouts per week" }),
        weeklyBars(weekly)
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Recent sessions" }),
        recent.length ? h("div", { class: "stack--sm" }, recent) : SM.ui.emptyState("Nothing to measure yet")
      ])
    ]));
  });
})();
