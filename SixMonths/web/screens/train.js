(function () {
  var ui = SM.ui;
  var h = ui.h;

  SM.route("train", function (container) {
    var plan = SM.planSnapshot();
    var today = SM.today();
    var todayDay = SM.daySnapshot(SM.weekday(today));
    var completedToday = SM.completedSessions().some(function (session) {
      return SM.iso(new Date(session.startedAt)) === SM.iso(today);
    });
    var weekStart = SM.completedWeekStart();
    var lastWeek = SM.sessionsInRange(weekStart, SM.addDays(weekStart, 6));
    var reviewResolved = SM.state.settings.lastProgressionReviewWeek === SM.iso(weekStart);

    var page = h("div", { class: "screen" }, [
      ui.screenHead({
        title: "Train",
        subtitle: SM.relativeLabel(today),
        actions: ui.textButton("Edit plan", function () { SM.go("edit-plan"); })
      })
    ]);

    var todayCard = ui.card([
      h("div", { class: "row--between" }, [
        h("span", { class: "kicker", text: "Today" }),
        h("span", { class: "label dim", text: todayDay.planDay.state })
      ]),
      h("span", { class: "title-lg", text: todayDay.planDay.state === "REST"
        ? "Rest day"
        : todayDay.entries.length === 0
          ? "Plan is empty"
          : (todayDay.planDay.name || "Training day") })
    ]);

    todayDay.entries.slice(0, 3).forEach(function (entry) {
      todayCard.appendChild(h("div", { class: "row--between" }, [
        h("span", { class: "body grow", text: entry.name }),
        ui.badge(SM.badgeText(entry.sets, entry.reps, entry.unit))
      ]));
    });
    if (todayDay.entries.length > 3) {
      todayCard.appendChild(h("span", { class: "label dim", text: "+" + (todayDay.entries.length - 3) + " more" }));
    }

    if (completedToday) {
      todayCard.appendChild(h("span", { class: "label green", text: "Done today" }));
    } else if (todayDay.planDay.state !== "REST" && todayDay.entries.length > 0) {
      todayCard.appendChild(ui.button("Start workout", function () {
        var session = SM.startSession(todayDay.planDay.id);
        SM.go("workout/" + session.id);
      }));
    }
    page.appendChild(todayCard);

    page.appendChild(ui.card([
      h("span", { class: "kicker", text: "Weekly progression" }),
      h("span", { class: "title-md", text: reviewResolved
        ? "Last week reviewed"
        : lastWeek.length > 0
          ? "Review last week"
          : "No review due" }),
      ui.button((reviewResolved ? "Open review" : "Review " + SM.formatShort(weekStart)), function () {
        SM.go("weekly-progression");
      }, { variant: "ghost" })
    ]));

    var planCard = ui.card([h("span", { class: "title-md", text: "Plan" })]);
    if (plan.trainingDays.length === 0) {
      planCard.appendChild(ui.emptyState("No training days yet"));
    }
    plan.days.forEach(function (day) {
      planCard.appendChild(h("button", {
        class: "list-row",
        type: "button",
        on: { click: function () { SM.go("day-editor/" + day.planDay.dayOfWeek); } }
      }, [
        h("span", { class: "label accent", text: SM.weekShort[day.planDay.dayOfWeek - 1], style: { width: "42px" } }),
        h("div", { class: "list-row__text" }, [
          h("span", { class: "body", text: day.planDay.name || (day.planDay.state === "TRAINING" ? "Training" : day.planDay.state === "OPTIONAL" ? "Optional" : "Rest") }),
          h("span", { class: "label dim", text: day.entries.length === 0 ? "No trainings" : day.totalTrainings + " trainings · " + day.totalSets + " sets" })
        ]),
        day.entries.length ? h("span", { class: "label muted", text: SM.categoryName(day.entries[0].category) }) : null,
        h("span", { class: "btn--text", text: "Edit" })
      ]));
    });
    page.appendChild(planCard);

    container.appendChild(page);
  });
})();
