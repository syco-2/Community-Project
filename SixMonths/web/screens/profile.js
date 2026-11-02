(function () {
  var h = SM.ui.h;

  SM.route("profile", function (container) {
    var settings = SM.state.settings;
    var streak = SM.state.streak;
    var unlocked = SM.cycleUnlocked();
    var remaining = SM.cycleDaysRemaining();
    var hasMessage = !!(settings.futureMessage && settings.futureMessage.trim());
    var messageStatus = !hasMessage
      ? "No message written"
      : unlocked
        ? "Ready to unlock"
        : "Locked · " + remaining + " days";

    container.appendChild(h("div", { class: "screen profile-page" }, [
      SM.ui.screenHead({ title: "Profile" }),

      SM.ui.card([
        h("div", { class: "row--between" }, [
          h("div", { class: "stack--xs" }, [
            h("span", { class: "kicker", text: "STREAK" }),
            h("span", { class: "profile-streak", text: (streak.currentStreak || 0) + " days" })
          ]),
          h("div", { class: "stack--xs", style: { alignItems: "flex-end" } }, [
            h("span", { class: "title-sm muted", text: "Longest " + (streak.longestStreak || 0) }),
            h("span", { class: "label dim", text: "Phase " + (settings.planPhase || 1) })
          ])
        ])
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Future message" }),
        h("p", { class: "body " + (unlocked ? "green" : "muted"), text: messageStatus }),
        SM.ui.button("Open future message", function () { SM.go("future-message"); }, { variant: "ghost" })
      ]),

      SM.ui.card([
        SM.ui.button("Stats", function () { SM.go("stats"); }, { variant: "ghost" }),
        SM.ui.button("Music", function () { SM.go("music"); }, { variant: "ghost" }),
        SM.ui.button("Settings", function () { SM.go("settings"); }, { variant: "ghost" })
      ]),

      h("p", { class: "profile-made", text: "made by Syco" })
    ]));
  });
})();
