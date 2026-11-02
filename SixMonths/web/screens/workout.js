(function () {
  var ui = SM.ui;
  var h = ui.h;
  var runtime = { key: null, value: 0, seconds: 0, countdown: 0, running: false, timer: null };

  function stopTimer() {
    if (runtime.timer) {
      window.clearInterval(runtime.timer);
      runtime.timer = null;
    }
  }

  function formatClock(seconds) {
    return Math.floor(seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
  }

  function resetRuntime(step) {
    stopTimer();
    runtime.key = SM.stepKey(step);
    runtime.value = step.entry.reps;
    runtime.seconds = 0;
    runtime.countdown = step.entry.unit === "SECONDS" ? 30 : 0;
    runtime.running = false;
  }

  function finishCelebration(onDone) {
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
      h("p", { class: "title-lg", text: "you made one more day", style: { textAlign: "center" } })
    ]), false);
    SM.lottie.mount(art, "fire", { loop: true });
    window.setTimeout(function () {
      layer.close();
      onDone();
    }, 2000);
  }

  SM.route("workout", function (container, params) {
    var sessionId = params.sessionId;
    var session = SM.sessionById(sessionId);
    var page = h("div", { class: "screen" });

    if (!session) {
      page.appendChild(ui.screenHead({ title: "Workout", back: function () { SM.back(); } }));
      page.appendChild(ui.card([
        h("span", { class: "title-md", text: "No active session" }),
        ui.button("Back", function () { SM.back(); }, { variant: "ghost" })
      ]));
      container.appendChild(page);
      return;
    }

    var steps = SM.circuitStepsForSession(sessionId);
    var logged = SM.loggedKeys(sessionId);
    var open = SM.firstOpenStep(steps, logged);
    var allDone = steps.length > 0 && open < 0;
    var stepIndex = steps.length === 0 ? 0 : allDone ? steps.length - 1 : open;
    var step = steps[stepIndex] || null;
    var dayName = "";
    if (session.planDayId) {
      var day = SM.state.days.filter(function (row) { return row.id === session.planDayId; })[0];
      if (day) dayName = day.name || SM.weekFull[day.dayOfWeek - 1];
    }

    page.appendChild(ui.screenHead({
      title: dayName || "Workout",
      back: function () { SM.back(); }
    }));

    if (window.SMMusic && window.SMMusic.playForWorkout) {
      window.SMMusic.playForWorkout();
    }

    if (session.status === "COMPLETED") {
      page.appendChild(ui.card([h("span", { class: "body", text: "This workout is finished." })]));
      container.appendChild(page);
      return;
    }

    if (steps.length === 0) {
      page.appendChild(ui.card([
        h("span", { class: "body muted", text: "Nothing planned for this day." }),
        ui.button("Back", function () { SM.back(); }, { variant: "ghost" })
      ]));
      container.appendChild(page);
      return;
    }

    if (step && runtime.key !== SM.stepKey(step)) resetRuntime(step);

    page.appendChild(h("div", { class: "row--between" }, [
      h("span", { class: "label muted", text: "Round " + (step.setIndex + 1) + " of " + SM.circuitRounds(steps) + " · " + stepIndex + " / " + steps.length + " sets" }),
      h("span", { class: "pill " + (step.block === "SKILL" ? "pill--blue" : step.block === "PLANCHE" ? "pill--orange" : "pill--muted"), text: SM.blockLabel(step.block) })
    ]));
    page.appendChild(ui.progressBar(steps.length ? stepIndex / steps.length : 0));

    var setDots = h("div", { class: "row", style: { gap: "6px" } });
    for (var dot = 0; dot < step.entry.sets; dot += 1) {
      setDots.appendChild(h("span", {
        style: {
          width: "10px",
          height: "10px",
          borderRadius: "50%",
          background: dot < step.setIndex ? "var(--blue)" : "var(--elevated)"
        }
      }));
    }

    var exerciseCard = ui.card([
      h("span", { class: "label dim", text: step.entry.label }),
      h("div", { class: "row--between" }, [
        h("span", { class: "title-lg", text: step.entry.name }),
        ui.badge(SM.badgeText(step.entry.sets, step.entry.reps, step.entry.unit))
      ]),
      h("span", { class: "small dim", text: SM.categoryName(step.entry.category) }),
      setDots,
      step.entry.formTip ? h("span", { class: "small green", text: step.entry.formTip }) : null
    ]);
    page.appendChild(exerciseCard);

    var timed = step.entry.unit === "SECONDS";
    var valueNode = h("div", { class: "display-xl", text: timed ? formatClock(runtime.seconds) : String(runtime.value), style: { textAlign: "center" } });
    var hintNode = h("span", { class: "label muted", text: runtime.countdown > 0 ? "starting in" : "" });
    var actionNode;

    function refreshPanel() {
      if (timed) {
        valueNode.textContent = runtime.countdown > 0 ? String(runtime.countdown) : formatClock(runtime.seconds);
        hintNode.textContent = runtime.countdown > 0 ? "starting in" : "";
        if (actionNode) actionNode.textContent = runtime.countdown > 0 ? "Start now" : runtime.running ? "Pause" : "Start";
      } else {
        valueNode.textContent = String(runtime.value);
      }
    }

    function tick() {
      if (runtime.countdown > 0) {
        runtime.countdown -= 1;
        if (runtime.countdown === 0) runtime.running = true;
      } else if (runtime.running) {
        runtime.seconds += 1;
      }
      refreshPanel();
    }

    function ensureTimer() {
      if (!runtime.timer) runtime.timer = window.setInterval(tick, 1000);
    }

    function toggleTimer() {
      if (runtime.countdown > 0) {
        runtime.countdown = 0;
        runtime.running = true;
        ensureTimer();
        refreshPanel();
        return;
      }
      runtime.running = !runtime.running;
      if (runtime.running) ensureTimer();
      refreshPanel();
    }

    if (timed) {
      var timerCard = ui.card([valueNode, hintNode]);
      var controls = h("div", { class: "row", style: { gap: "10px" } });
      controls.appendChild(ui.textButton("Reset", function () {
        runtime.seconds = 0;
        runtime.countdown = 30;
        runtime.running = false;
        refreshPanel();
      }, "muted"));
      actionNode = ui.button(runtime.countdown > 0 ? "Start now" : "Start", toggleTimer);
      actionNode.style.flex = "1";
      controls.appendChild(actionNode);
      timerCard.appendChild(controls);
      page.appendChild(timerCard);
      ensureTimer();
    } else {
      var counterCard = ui.card([valueNode]);
      counterCard.appendChild(h("div", { class: "row", style: { justifyContent: "center", gap: "24px" } }, [
        ui.iconButton("minus", function () {
          runtime.value = Math.max(1, runtime.value - 1);
          refreshPanel();
        }, "Less"),
        ui.iconButton("plus", function () {
          runtime.value = Math.min(200, runtime.value + 1);
          refreshPanel();
        }, "More")
      ]));
      page.appendChild(counterCard);
    }

    page.appendChild(ui.button(timed ? "Log " + formatClock(runtime.seconds) : "Log set " + (step.setIndex + 1) + " of " + step.entry.sets, function () {
      if (allDone) return;
      var value = timed ? Math.max(1, runtime.seconds) : runtime.value;
      SM.logSet(sessionId, step.entry, step.setIndex, value);
      stopTimer();
      runtime.key = null;
      SM.refresh();
    }, { disabled: allDone }));

    if (allDone) {
      page.appendChild(ui.button("Finish workout", function () {
        stopTimer();
        SM.completeSession(sessionId);
        finishCelebration(function () { SM.go("dashboard"); });
      }));
    }

    container.appendChild(page);

    SM.cleanup = stopTimer;
  });
})();
