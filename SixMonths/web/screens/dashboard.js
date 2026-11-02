(function () {
  var ui = SM.ui;
  var h = ui.h;

  function celebration(title, animationName) {
    var art = h("div", { class: "lottie-layer__art" });
    var layer = ui.overlay(h("div", {
      class: "stack",
      style: {
        position: "fixed",
        inset: "0",
        background: "#000000",
        alignItems: "center",
        justifyContent: "center",
        gap: "18px",
        padding: "24px"
      }
    }, [
      art,
      h("p", { class: "title-lg", text: title, style: { textAlign: "center" } })
    ]), false);
    SM.lottie.mount(art, animationName || "fire", { loop: true });
    return layer;
  }

  function applyProgression(action) {
    SM.state.entries.forEach(function (entry) {
      var exercise = SM.state.exercises.filter(function (row) { return row.id === entry.exerciseId; })[0];
      var seconds = exercise && exercise.unit === "SECONDS";
      if (action === "ADD_REP") {
        entry.reps = Math.min(entry.reps + (seconds ? 10 : 1), seconds ? 600 : 100);
      } else if (action === "ADD_SET") {
        entry.sets = Math.min(entry.sets + 1, 10);
      } else {
        entry.reps = Math.max(Math.round(entry.reps * 0.8), seconds ? 10 : 1);
      }
    });
    SM.state.settings.planPhase += 1;
    SM.state.settings.planPhaseStartedAt = Date.now();
    SM.commit(true);
    ui.toast("Plan updated");
  }

  SM.route("dashboard", function (container) {
    var plan = SM.planSnapshot();
    var today = SM.today();
    var todayDay = SM.daySnapshot(SM.weekday(today));
    var streak = SM.state.streak;
    var weekStart = SM.startOfWeek(today);
    var weekSessions = SM.sessionsInRange(weekStart, SM.addDays(weekStart, 6));
    var planned = plan.trainingDays.length;
    var trainingIds = plan.trainingDays.map(function (day) { return day.planDay.id; });
    var doneIds = {};
    weekSessions.forEach(function (session) {
      if (trainingIds.indexOf(session.planDayId) >= 0) doneIds[session.planDayId] = true;
    });
    var doneDays = Object.keys(doneIds).length;
    var completedToday = SM.completedSessions().some(function (session) {
      return SM.iso(new Date(session.startedAt)) === SM.iso(today) && session.planDayId === todayDay.planDay.id;
    });
    var canTrain = todayDay.planDay.state !== "REST" && todayDay.entries.length > 0 && !completedToday;
    var weeklyGoalDone = planned > 0 && doneDays >= planned;

    var page = h("div", { class: "screen" });

    page.appendChild(h("div", { class: "screen-top" }, [
      h("img", { src: "web/img/logo.png", alt: "", width: "42", height: "42" }),
      h("div", { class: "screen-top__text" }, [
        h("div", { class: "row", style: { alignItems: "baseline", gap: "6px" } }, [
          h("h1", { class: "title-lg", text: "Six Months" }),
          h("span", { class: "brand-sub", text: "made by Syco" })
        ]),
        h("span", { class: "label dim", text: SM.relativeLabel(today) })
      ]),
      ui.textButton("Settings", function () { SM.go("settings"); })
    ]));

    var todayCard = ui.card([
      h("div", { class: "row--between" }, [
        h("span", { class: "kicker", text: "Today" }),
        h("span", { class: "label dim", text: todayDay.planDay.state })
      ]),
      todayDay.planDay.state === "REST"
        ? h("span", { class: "title-lg", text: "Rest day" })
        : todayDay.planDay.state === "OPTIONAL"
          ? h("span", { class: "title-lg", text: "Optional" })
          : h("span", { class: "title-lg", text: todayDay.entries.length === 0 ? "Training day" : (todayDay.planDay.name || "Training day") })
    ]);

    if (todayDay.planDay.state !== "REST" && todayDay.entries.length > 0) {
      todayDay.entries.slice(0, 4).forEach(function (entry) {
        todayCard.appendChild(h("div", { class: "row--between" }, [
          h("div", { class: "stack--xs grow" }, [
            h("span", { class: "body", text: entry.name }),
            h("span", { class: "label dim", text: SM.categoryName(entry.category) })
          ]),
          ui.badge(SM.badgeText(entry.sets, entry.reps, entry.unit))
        ]));
      });
      if (todayDay.entries.length > 4) {
        todayCard.appendChild(h("span", { class: "label dim", text: "+" + (todayDay.entries.length - 4) + " more" }));
      }
      todayCard.appendChild(h("span", { class: "small dim", text: todayDay.totalTrainings + " trainings · " + todayDay.totalSets + " sets" }));
    }

    if (completedToday) {
      todayCard.appendChild(h("span", { class: "label green", text: "Done today" }));
    } else if (canTrain) {
      todayCard.appendChild(ui.button("Start Workout", function () {
        var session = SM.startSession(todayDay.planDay.id);
        SM.go("workout/" + session.id);
      }));
    } else {
      todayCard.appendChild(ui.button("Edit plan", function () { SM.go("edit-plan"); }, { variant: "ghost" }));
    }
    page.appendChild(todayCard);

    var streakCard = ui.card([
      h("div", { class: "row--between" }, [
        h("div", { class: "stack--xs" }, [
          h("span", { class: "kicker", text: "Streak" }),
          h("div", { class: "row", style: { alignItems: "baseline", gap: "8px" } }, [
            h("span", { class: "display", text: String(streak.currentStreak), style: { color: "var(--orange)", fontVariantNumeric: "tabular-nums" } }),
            h("span", { class: "title-sm muted", text: streak.currentStreak === 1 ? "day" : "days" })
          ])
        ]),
        h("div", { class: "lottie-flame", style: { width: "62px", height: "62px", opacity: streak.currentStreak > 0 ? "1" : "0.28" } })
      ]),
      h("div", { class: "row", style: { gap: "18px" } }, [
        h("div", { class: "stack--xs" }, [h("span", { class: "title-sm", text: String(streak.longestStreak) }), h("span", { class: "label dim", text: "Longest" })]),
        h("div", { class: "stack--xs" }, [h("span", { class: "title-sm", text: String(streak.freezeTokens) }), h("span", { class: "label dim", text: "Freezes" })]),
        h("div", { class: "stack--xs" }, [h("span", { class: "title-sm", text: String(streak.totalCompletedSessions) }), h("span", { class: "label dim", text: "Sessions" })])
      ]),
      h("div", { class: "row", style: { gap: "16px", marginTop: "4px" } }, [
        ui.ring(doneDays, planned),
        h("div", { class: "stack--xs" }, [
          h("span", { class: "title-sm", text: "This week" }),
          h("span", { class: "small muted", text: doneDays + " / " + planned })
        ])
      ])
    ]);
    page.appendChild(streakCard);

    page.appendChild(ui.card([
      h("span", { class: "kicker", text: "Next reminder" }),
      h("span", { class: "title-md", text: SM.nextReminder() || "None" })
    ]));

    page.appendChild(ui.card([
      ui.button("Music", function () { SM.go("music"); }, { variant: "ghost" }),
      ui.button("Stats", function () { SM.go("stats"); }, { variant: "ghost" })
    ]));

    container.appendChild(page);

    var flame = page.querySelector(".lottie-flame");
    if (flame) SM.lottie.mount(flame, "fire", { loop: true });

    if (weeklyGoalDone && SM.state.settings.lastTrophyWeek !== SM.iso(weekStart)) {
      var show = celebration("you made one more week", "trophy");
      SM.state.settings.lastTrophyWeek = SM.iso(weekStart);
      SM.commit();
      window.setTimeout(function () { show.close(); }, 2000);
    }

    if (SM.progressionDue()) {
      var suggestion = SM.progressionSuggestion(SM.state.streak.totalCompletedSessions);
      ui.dialog({
        title: "Phase " + SM.state.settings.planPhase + " complete",
        text: suggestion.title + ". " + suggestion.detail,
        confirmLabel: "Apply",
        onConfirm: function () { applyProgression(suggestion.action); }
      });
    }
  });
})();
