(function () {
  var ui = SM.ui;
  var h = ui.h;

  function page(children) {
    return h("div", { class: "screen" }, children);
  }

  function frame(options) {
    var step = options.step;
    var container = page([
      ui.screenHead({
        title: options.title,
        subtitle: options.subtitle,
        back: options.back,
        progress: step ? [step, 7] : null
      }),
      h("div", { class: "stack" }, options.children),
      h("div", { class: "stack--sm", style: { marginTop: "8px" } }, [
        options.primary,
        options.secondary
      ])
    ]);
    return container;
  }

  function goStep(step, route) {
    SM.state.settings.onboardingStep = step;
    SM.commit();
    SM.go(route);
  }

  function dayStateColor(state) {
    return state === "TRAINING" ? "pill--blue" : state === "OPTIONAL" ? "pill--orange" : "pill--muted";
  }

  function nextState(state) {
    return state === "REST" ? "TRAINING" : state === "TRAINING" ? "OPTIONAL" : "REST";
  }

  function stateName(state) {
    return state === "TRAINING" ? "Training" : state === "OPTIONAL" ? "Optional" : "Rest";
  }

  SM.route("onboarding/welcome", function (container) {
    SM.state.settings.onboardingStep = 1;
    SM.commit();
    container.appendChild(page([
      h("div", { class: "stack", style: { alignItems: "center", paddingTop: "56px", gap: "28px" } }, [
        h("img", { src: "web/img/logo.png", alt: "", width: "132", height: "132" }),
        h("h1", { class: "title-hero", text: "Six Months", style: { textAlign: "center" } }),
        ui.dots(1, 7),
        ui.button("Get Started", function () { goStep(2, "onboarding/days"); })
      ])
    ]));
  });

  SM.route("onboarding/days", function (container) {
    var plan = SM.planSnapshot();
    var trainingCount = plan.trainingDays.length;
    var sets = plan.trainingDays.reduce(function (sum, day) { return sum + day.totalSets; }, 0);

    container.appendChild(frame({
      title: "Training days",
      step: 2,
      back: function () { goStep(1, "onboarding/welcome"); },
      children: [
        h("p", { class: "title-md", text: trainingCount + " training " + (trainingCount === 1 ? "day" : "days") + " · " + sets + " sets" }),
        h("div", { class: "stack--sm" }, plan.days.map(function (day) {
          var state = day.planDay.state;
          return h("button", {
            class: "card card--button",
            type: "button",
            on: {
              click: function () {
                var row = SM.dayRow(day.planDay.dayOfWeek);
                row.state = nextState(row.state);
                SM.commit(true);
              }
            }
          }, [
            h("div", { class: "row--between" }, [
              h("div", { class: "stack--xs" }, [
                h("span", { class: "title-md", text: SM.weekFull[day.planDay.dayOfWeek - 1] }),
                state === "TRAINING" ? h("span", { class: "small dim", text: day.totalTrainings + " trainings · " + day.totalSets + " sets" }) : null
              ]),
              h("span", { class: "pill " + dayStateColor(state), text: stateName(state) })
            ])
          ]);
        }))
      ],
      primary: ui.button("Continue", function () { goStep(3, "onboarding/plan"); }),
      secondary: ui.button("Back", function () { goStep(1, "onboarding/welcome"); }, { variant: "ghost" })
    }));
  });

  SM.route("onboarding/plan", function (container) {
    var plan = SM.planSnapshot();
    var trainingDays = plan.trainingDays;
    var totalSets = trainingDays.reduce(function (sum, day) { return sum + day.totalSets; }, 0);
    var totalTrainings = trainingDays.reduce(function (sum, day) { return sum + day.totalTrainings; }, 0);

    container.appendChild(frame({
      title: "Your plan",
      step: 3,
      back: function () { goStep(2, "onboarding/days"); },
      children: trainingDays.length === 0
        ? [h("p", { class: "title-md", text: "No training days yet" })]
        : [
            h("p", { class: "title-md", text: trainingDays.length + " training " + (trainingDays.length === 1 ? "day" : "days") + " · " + totalSets + " sets · " + totalTrainings + " trainings" }),
            h("div", { class: "stack--sm" }, trainingDays.map(function (day) {
              return h("button", {
                class: "card card--button",
                type: "button",
                on: { click: function () { SM.go("day-editor/" + day.planDay.dayOfWeek); } }
              }, [
                h("div", { class: "row--between" }, [
                  h("div", { class: "stack--xs" }, [
                    h("span", { class: "title-md", text: day.planDay.name || SM.weekFull[day.planDay.dayOfWeek - 1] }),
                    h("span", { class: "small dim", text: day.entries.length === 0 ? "No trainings" : day.totalTrainings + " trainings · " + day.totalSets + " sets" })
                  ]),
                  h("span", { class: "label accent", text: day.planDay.state === "OPTIONAL" ? "EDIT · OPTIONAL" : "EDIT" })
                ])
              ]);
            }))
          ],
      primary: ui.button("Continue", function () { goStep(4, "onboarding/reminders"); }),
      secondary: ui.button("Back to training days", function () { goStep(2, "onboarding/days"); }, { variant: "ghost" })
    }));
  });

  SM.route("onboarding/reminders", function (container) {
    var settings = SM.state.settings;
    var plan = SM.planSnapshot();
    var days = settings.reminderDays.length
      ? settings.reminderDays.slice()
      : plan.trainingDays.map(function (day) { return day.planDay.dayOfWeek; });

    function toggle(dayOfWeek) {
      var index = days.indexOf(dayOfWeek);
      if (index >= 0) days.splice(index, 1);
      else days.push(dayOfWeek);
      settings.reminderDays = days.slice().sort();
      SM.commit();
      SM.refresh();
    }

    var timeInput = h("input", { type: "time", class: "input", value: settings.reminderTime });
    timeInput.addEventListener("change", function () {
      settings.reminderTime = timeInput.value || "19:00";
      SM.commit(true);
    });

    var notifyButton = ui.button("Enable notifications", function () {
      if (!window.Notification) {
        ui.toast("Notifications are not available");
        return;
      }
      window.Notification.requestPermission().then(function (permission) {
        ui.toast(permission === "granted" ? "Notifications allowed" : "Notifications blocked");
        SM.refresh();
      });
    }, { variant: "ghost" });

    container.appendChild(frame({
      title: "Reminders",
      step: 4,
      back: function () { goStep(3, "onboarding/plan"); },
      children: [
        ui.card([
          h("span", { class: "title-md", text: "Reminder time" }),
          h("div", { class: "row--between" }, [
            h("span", { class: "title-lg accent", text: settings.reminderTime }),
            timeInput
          ]),
          h("span", { class: "title-sm muted", text: "Reminder days" }),
          h("div", { class: "chip-row" }, SM.weekShort.map(function (label, index) {
            var dayOfWeek = index + 1;
            return ui.chip(label, days.indexOf(dayOfWeek) >= 0, function () { toggle(dayOfWeek); });
          }))
        ]),
        ui.card([
          h("span", { class: "title-md", text: "Notifications" }),
          window.Notification && window.Notification.permission === "granted"
            ? h("span", { class: "label green", text: "Notifications allowed" })
            : notifyButton
        ])
      ],
      primary: ui.button("Continue", function () { goStep(5, "onboarding/music"); }),
      secondary: ui.button("Back", function () { goStep(3, "onboarding/plan"); }, { variant: "ghost" })
    }));
  });

  SM.route("onboarding/music", function (container) {
    var input = h("input", { type: "file", accept: "audio/*", multiple: true, style: { display: "none" } });
    input.addEventListener("change", function () {
      var files = Array.prototype.slice.call(input.files || []);
      Promise.all(files.map(function (file) {
        var id = SM.uid();
        return SM.readFile(file, false).then(function (buffer) {
          return SM.filePut("music:" + id, new Blob([buffer], { type: file.type || "audio/mpeg" })).then(function () {
            SM.state.music.push({ id: id, name: file.name, size: file.size });
          });
        });
      })).then(function () {
        SM.commit(true);
      });
    });

    var tracks = SM.state.music || [];

    container.appendChild(frame({
      title: "Your music",
      step: 5,
      back: function () { goStep(4, "onboarding/reminders"); },
      children: [
        ui.card(tracks.length === 0
          ? [h("span", { class: "body muted", text: "No music selected" })]
          : tracks.map(function (track) {
              return h("div", { class: "row--between" }, [
                h("span", { class: "body grow", text: track.name }),
                ui.iconButton("trash", function () {
                  SM.state.music = tracks.filter(function (row) { return row.id !== track.id; });
                  SM.fileDelete("music:" + track.id);
                  SM.commit(true);
                }, "Remove")
              ]);
            })
        ),
        ui.button("Add music", function () { input.click(); }, { variant: "ghost", icon: "upload" }),
        input
      ],
      primary: ui.button("Continue", function () { goStep(6, "onboarding/future-message"); }),
      secondary: ui.button("Skip for now", function () { goStep(6, "onboarding/future-message"); }, { variant: "ghost" })
    }));
  });

  SM.route("onboarding/future-message", function (container) {
    var settings = SM.state.settings;
    var input = h("textarea", { class: "textarea", placeholder: "Dear future me…", maxlength: "2000" });
    input.value = settings.futureMessage || "";
    var counter = h("span", { class: "label dim", text: (input.value.length) + " / 2000" });
    input.addEventListener("input", function () {
      counter.textContent = input.value.length + " / 2000";
    });

    container.appendChild(frame({
      title: "Future message",
      step: 6,
      back: function () { goStep(5, "onboarding/music"); },
      children: [
        ui.card([input, counter]),
        ui.card([h("span", { class: "title-md", text: "Unlocks after six months" })])
      ],
      primary: ui.button("Lock this message", function () {
        settings.futureMessage = input.value.slice(0, 2000);
        settings.futureMessageRevealed = false;
        SM.commit();
        goStep(7, "onboarding/review");
      }),
      secondary: ui.button("Back", function () { goStep(5, "onboarding/music"); }, { variant: "ghost" })
    }));
  });

  SM.route("onboarding/review", function (container) {
    var plan = SM.planSnapshot();
    var settings = SM.state.settings;
    var review = SM.coachReview(plan);
    var trainingDays = plan.trainingDays;
    var totalSets = trainingDays.reduce(function (sum, day) { return sum + day.totalSets; }, 0);
    var totalTrainings = trainingDays.reduce(function (sum, day) { return sum + day.totalTrainings; }, 0);

    var children = [];
    if (trainingDays.length === 0) {
      children.push(h("p", { class: "title-md", text: "No training days yet" }));
    } else {
      children.push(ui.card([
        h("span", { class: "title-md", text: "Your week" }),
        h("span", { class: "body muted", text: totalTrainings + " trainings · " + totalSets + " sets across " + trainingDays.length + " days" }),
        h("span", { class: "small dim", text: trainingDays.map(function (day) {
          return (day.planDay.name || SM.weekShort[day.planDay.dayOfWeek - 1]) + " (" + day.totalSets + " sets)";
        }).join(" · ") })
      ]));
      children.push(ui.card([
        h("span", { class: "title-md", text: "Sets by category" }),
        h("div", { class: "stack--xs" }, SM.categories.map(function (category) {
          var sets = review.setsByCategory[category] || 0;
          return h("span", { class: "body " + (sets === 0 ? "dim" : ""), text: SM.categoryName(category) + ": " + sets + " sets" });
        }))
      ]));
      if (review.suggestions.length) {
        children.push(ui.card([
          h("span", { class: "title-md", text: "Coach notes" }),
          h("div", { class: "stack--sm" }, review.suggestions.slice(0, 4).map(function (suggestion) {
            return h("div", { class: "stack--xs" }, [
              h("span", { class: "title-sm " + (suggestion.severity === "CRITICAL" ? "red" : suggestion.severity === "WARNING" ? "amber" : "green"), text: suggestion.title }),
              h("span", { class: "small muted", text: suggestion.detail })
            ]);
          }))
        ]));
      }
    }

    children.push(ui.card([
      h("span", { class: "title-md", text: "Reminders & music" }),
      h("span", { class: "body muted", text: settings.reminderDays.length === 0
        ? "No reminder days selected."
        : settings.reminderTime + " on " + settings.reminderDays.slice().sort().map(function (dayOfWeek) {
            return SM.weekShort[dayOfWeek - 1];
          }).join(", ") }),
      h("span", { class: "small dim", text: !SM.state.music.length ? "No music selected. Optional." : SM.state.music.length + " tracks in the playlist." })
    ]));

    container.appendChild(frame({
      title: "Coach review",
      step: 7,
      back: function () { goStep(6, "onboarding/future-message"); },
      children: children,
      primary: ui.button("Finish and start", function () {
        var now = Date.now();
        settings.onboardingCompleted = true;
        settings.onboardingStep = 7;
        if (!settings.planPhaseStartedAt) {
          settings.planPhase = 1;
          settings.planPhaseStartedAt = now;
        }
        if (!settings.cycleStartedAt) settings.cycleStartedAt = now;
        SM.commit();
        SM.evaluateStreak();
        SM.go("dashboard");
      }),
      secondary: ui.button("Back to adjust", function () { goStep(6, "onboarding/future-message"); }, { variant: "ghost" })
    }));
  });
})();
