(function () {
  function lockCard(remaining, unlock) {
    var icon = SM.ui.svg("lock");
    icon.style.width = "46px";
    icon.style.height = "46px";

    return SM.ui.card([
      SM.ui.h("div", { class: "center", style: { display: "flex", justifyContent: "center", padding: "6px 0" } }, [
        SM.ui.h("div", {
          style: {
            width: "120px",
            height: "120px",
            display: "grid",
            placeItems: "center",
            borderRadius: "50%",
            background: "rgba(255, 209, 102, 0.08)",
            border: "1px solid rgba(255, 209, 102, 0.35)",
            color: "#FFD166"
          }
        }, [icon])
      ]),
      SM.ui.h("h2", { class: "title-xl center", style: { color: "#FFD166" }, text: "Locked" }),
      SM.ui.h("p", {
        class: "body muted center",
        text: remaining + (remaining === 1 ? " day" : " days")
      }),
      unlock
        ? SM.ui.h("p", { class: "small dim center", text: "Unlocks on " + SM.formatShort(unlock) })
        : null
    ]);
  }

  function messageCard(message) {
    return SM.ui.card([
      SM.ui.h("p", { class: "label", style: { color: "#FFD166" }, text: "DEAR FUTURE ME" }),
      SM.ui.h("p", { class: "body-lg", text: message })
    ]);
  }

  SM.route("future-message", function (container) {
    var settings = SM.state.settings;
    var message = settings.futureMessage || "";
    var unlocked = SM.cycleUnlocked();
    var remaining = SM.cycleDaysRemaining();
    var unlock = SM.cycleUnlockDate();
    var hasMessage = message.trim().length > 0;

    if (unlocked && hasMessage && !settings.futureMessageRevealed) {
      settings.futureMessageRevealed = true;
      SM.commit();
    }

    var body = [];
    if (!hasMessage) {
      body.push(SM.ui.card([
        SM.ui.h("p", { class: "title-md", text: "No message was written" })
      ]));
    } else if (!unlocked) {
      body.push(lockCard(remaining, unlock));
    } else {
      body.push(messageCard(message));
    }

    if (unlocked && hasMessage) {
      body.push(SM.ui.button("Continue with last plan", function () {
        var now = Date.now();
        settings.onboardingCompleted = true;
        settings.planPhase = 1;
        settings.planPhaseStartedAt = now;
        settings.cycleStartedAt = now;
        settings.cycleIsExplicit = true;
        settings.futureMessageRevealed = false;
        settings.lastProgressionReviewWeek = null;
        SM.resetForNewCycle();
        SM.commit(true);
        SM.go("dashboard");
      }));
      body.push(SM.ui.button("Start again", function () {
        var now = Date.now();
        settings.cycleStartedAt = now;
        settings.cycleIsExplicit = true;
        settings.futureMessageRevealed = false;
        settings.onboardingCompleted = false;
        settings.onboardingStep = 1;
        SM.resetForNewCycle();
        SM.commit(true);
        SM.go("onboarding/welcome");
      }, { variant: "ghost" }));
    }

    container.appendChild(SM.ui.h("div", { class: "screen" }, [
      SM.ui.screenHead({
        title: "Future message",
        back: function () { SM.back(); }
      })
    ].concat(body)));
  });
})();
