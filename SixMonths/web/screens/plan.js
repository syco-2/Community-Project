(function () {
  var h = SM.ui.h;

  function normalize(raw) {
    return (raw || "").toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim().replace(/ +/g, " ");
  }

  var STATIC_SKILL_WORDS = ["handstand", "planche", "front lever", "back lever", "lever", "human flag", "l sit", "lsit", "v sit", "maltese", "iron cross", "skin the cat"];
  var PUSH_WORDS = ["push", "dip", "hspu", "handstand", "planche", "pike", "press", "bench", "maltese", "protraction", "archer", "pppu"];
  var PULL_WORDS = ["pull", "row", "chin", "curl", "lever", "muscle up", "flag"];
  var CORE_WORDS = ["hollow", "plank", "l sit", "lsit", "leg raise", "core", "dragon flag", "v sit"];
  var LEG_WORDS = ["squat", "lunge", "calf", "pistol", "nordic", "hamstring", "glute", "bridge", "hinge"];

  function isStaticSkill(entry) {
    if (entry.category === "SKILL") return true;
    var name = normalize(entry.name);
    return STATIC_SKILL_WORDS.some(function (word) { return name.indexOf(word) >= 0; });
  }

  function laneOf(entry) {
    if (entry.category === "PUSH") return "PUSH";
    if (entry.category === "PULL") return "PULL";
    if (entry.category === "CORE") return "CORE";
    if (entry.category === "LEGS") return "LEGS";
    if (entry.category === "MOBILITY") return null;
    var name = normalize(entry.name);
    if (LEG_WORDS.some(function (word) { return name.indexOf(word) >= 0; })) return "LEGS";
    if (CORE_WORDS.some(function (word) { return name.indexOf(word) >= 0; })) return "CORE";
    if (PULL_WORDS.some(function (word) { return name.indexOf(word) >= 0; })) return "PULL";
    if (PUSH_WORDS.some(function (word) { return name.indexOf(word) >= 0; })) return "PUSH";
    return null;
  }

  function setsForLane(day, lane) {
    return day.entries.filter(function (entry) { return laneOf(entry) === lane; })
      .reduce(function (sum, entry) { return sum + entry.sets; }, 0);
  }

  function longestPushSkillRun(plan) {
    var flags = [1, 2, 3, 4, 5, 6, 7].map(function (weekday) {
      var day = plan.days.filter(function (row) { return row.planDay.dayOfWeek === weekday; })[0];
      return !!day && day.planDay.state !== "REST" && day.entries.some(function (entry) {
        return laneOf(entry) === "PUSH" || isStaticSkill(entry);
      });
    });
    if (flags.every(Boolean)) return 7;
    if (!flags.some(Boolean)) return 0;
    var start = flags.indexOf(false) + 1;
    var run = 0;
    var longest = 0;
    for (var offset = 0; offset < 7; offset += 1) {
      if (flags[(start + offset) % 7]) {
        run += 1;
        longest = Math.max(longest, run);
      } else {
        run = 0;
      }
    }
    return longest;
  }

  function audit(plan) {
    var activeDays = plan.days.filter(function (day) {
      return day.planDay.state !== "REST" && day.entries.length > 0;
    });
    var trainingDays = plan.days.filter(function (day) { return day.planDay.state === "TRAINING"; }).length;
    var weeklySets = activeDays.reduce(function (sum, day) { return sum + day.totalSets; }, 0);
    var pushSets = activeDays.reduce(function (sum, day) { return sum + setsForLane(day, "PUSH"); }, 0);
    var pullSets = activeDays.reduce(function (sum, day) { return sum + setsForLane(day, "PULL"); }, 0);
    var coreSets = activeDays.reduce(function (sum, day) { return sum + setsForLane(day, "CORE"); }, 0);
    var legSets = activeDays.reduce(function (sum, day) { return sum + setsForLane(day, "LEGS"); }, 0);
    var skillDays = activeDays.filter(function (day) {
      return day.entries.some(isStaticSkill);
    }).length;
    var maxDailyPush = plan.days.reduce(function (max, day) { return Math.max(max, setsForLane(day, "PUSH")); }, 0);
    var maxConsecutiveLoad = longestPushSkillRun(plan);
    var skillCircuitDays = activeDays.filter(function (day) {
      var skills = day.entries.filter(isStaticSkill);
      if (!skills.length) return false;
      var lastSkillOrder = skills.reduce(function (max, entry) { return Math.max(max, entry.sortOrder); }, 0);
      return day.entries.filter(function (entry) { return !isStaticSkill(entry); })
        .some(function (entry) { return entry.sortOrder < lastSkillOrder; });
    });
    var shortSkillRestDays = activeDays.filter(function (day) {
      return day.entries.some(function (entry) { return isStaticSkill(entry) && entry.restSeconds < 120; });
    });
    var allSevenTraining = plan.days.length === 7 && plan.days.every(function (day) {
      return day.planDay.state === "TRAINING";
    });

    var pros = [];
    if (skillDays > 0 && !skillCircuitDays.length && !shortSkillRestDays.length) {
      pros.push({
        title: "Skills are trained fresh",
        detail: "Static skill work leads the session with at least 2-3 min rest. That is how handstand and planche actually progress."
      });
    }
    if (skillDays >= 3) {
      pros.push({
        title: "Skill frequency is high",
        detail: skillDays + " skill days per week. Frequent short practice beats one long session."
      });
    }
    if (pushSets > 0 && maxDailyPush <= 12) {
      pros.push({
        title: "Push volume is sustainable",
        detail: "The biggest push day is " + maxDailyPush + " sets, inside the 12-set ceiling. You can add quality reps before adding sets."
      });
    }
    if (maxConsecutiveLoad >= 1 && maxConsecutiveLoad <= 3) {
      pros.push({
        title: "Joints get recovery",
        detail: "The longest push/skill run is " + maxConsecutiveLoad + " days. That keeps elbow, wrist and shoulder tendinitis risk low."
      });
    }
    if (coreSets > 0) {
      pros.push({
        title: "Core is in the plan",
        detail: coreSets + " core sets per week. Hollow body and L-sit carry handstands, levers and planche."
      });
    }
    if (legSets > 0) {
      pros.push({
        title: "Legs are covered",
        detail: legSets + " leg sets per week. Loaded legs keep the base durable, even in a bodyweight plan."
      });
    }
    if (pushSets > 0 && pullSets > 0 && pushSets / pullSets >= 0.67 && pushSets / pullSets <= 1.5) {
      pros.push({
        title: "Push and pull are balanced",
        detail: "Push " + pushSets + " vs pull " + pullSets + " sets per week. The shoulders stay centred instead of drifting forward."
      });
    }
    if (!activeDays.length) {
      pros.push({
        title: "A blank canvas",
        detail: "No exercises yet, so nothing is overloaded. Add training days in the plan."
      });
    }
    pros = pros.slice(0, 6);

    var risks = [];
    if (trainingDays === 0) {
      risks.push({
        title: "No training day",
        detail: "Every day is Rest or unmarked. Mark at least two days as Training.",
        critical: true
      });
    }
    if (skillCircuitDays.length) {
      var circuitNames = skillCircuitDays.map(function (day) { return SM.weekShort[day.planDay.dayOfWeek - 1]; }).join(", ");
      risks.push({
        title: "Skills are buried in the circuit (" + circuitNames + ")",
        detail: "Skills require maximum nervous system freshness. Do not train them in a circuit. Train them first with 2-3 min rest.",
        critical: true
      });
    } else if (shortSkillRestDays.length) {
      var shortest = Math.min.apply(null, shortSkillRestDays
        .map(function (day) { return Math.min.apply(null, day.entries.filter(isStaticSkill).map(function (entry) { return entry.restSeconds; })); }));
      risks.push({
        title: "Skill rest is too short",
        detail: "Static skills are planned with " + shortest + "s rest. Use 2-3 min between skill sets so the nervous system recovers.",
        critical: true
      });
    }
    if (skillDays === 0 && trainingDays > 0) {
      risks.push({
        title: "No static skill work",
        detail: "No handstand, planche or lever work found. Add 2-3 short skill sessions at the start of a session, not at the end."
      });
    }
    if (maxConsecutiveLoad > 3) {
      risks.push({
        title: "Push or skill on " + maxConsecutiveLoad + " days in a row",
        detail: "High risk of elbow, wrist and shoulder tendinitis. Put at least one full rest day between loading blocks.",
        critical: true
      });
    }
    if (allSevenTraining) {
      risks.push({
        title: "No rest day",
        detail: "Seven training days in a row. Muscles and tendons adapt during rest; turn your lowest-volume day into a full rest day.",
        critical: true
      });
    }
    if (maxDailyPush > 12) {
      var biggest = activeDays.slice().sort(function (a, b) { return setsForLane(b, "PUSH") - setsForLane(a, "PUSH"); })[0];
      var biggestName = biggest ? SM.weekFull[biggest.planDay.dayOfWeek - 1] : "one day";
      risks.push({
        title: "Massive push day (" + biggestName + " · " + maxDailyPush + " sets)",
        detail: "Massive push volume. Consider reducing sets or varying exercises week by week.",
        critical: true
      });
    }
    if (pushSets > 0 && pullSets === 0) {
      risks.push({
        title: "No pulling work",
        detail: "Pressing without pulling is the fastest route to shoulder pain. Add pull-ups, rows or front-lever pulls."
      });
    } else if (pullSets > 0 && pushSets / pullSets > 1.5) {
      risks.push({
        title: "Push is outrunning pull",
        detail: "Push " + pushSets + " vs pull " + pullSets + " sets per week. Add pulling until the ratio sits between 0.8 and 1.3."
      });
    }
    if (coreSets === 0 && trainingDays > 0) {
      risks.push({
        title: "Core is missing",
        detail: "Handstands, levers and planche all hang off the core. Add 6-10 hollow-body, L-sit or leg-raise sets per week."
      });
    }
    if (legSets === 0 && trainingDays > 0) {
      risks.push({
        title: "Legs are missing",
        detail: "A calisthenics plan still needs legs. Add one block: squats, split squats, calf raises and a hinge."
      });
    }
    risks = risks.sort(function (a, b) { return (b.critical ? 1 : 0) - (a.critical ? 1 : 0); }).slice(0, 8);

    var rules = trainingDays === 0 ? [
      { title: "Skill freshness", grade: 0, verdict: "No training day to judge", advice: "Mark at least two training days.", passed: false },
      { title: "Joint frequency", grade: 0, verdict: "No loading days", advice: "Give the plan a rhythm first.", passed: false },
      { title: "Daily volume", grade: 0, verdict: "No volume to cap", advice: "Add the exercises you actually train.", passed: false }
    ] : [
      skillDays === 0
        ? { title: "Skill freshness", grade: 3, verdict: "No handstand or planche work", advice: "Train handstand and planche first, with 2-3 min rest, on their own days.", passed: false }
        : skillCircuitDays.length
          ? { title: "Skill freshness", grade: 2, verdict: "Skills are mixed into the circuit", advice: "Move every static skill to the start of the session, before any strength work.", passed: false }
          : shortSkillRestDays.length
            ? { title: "Skill freshness", grade: 5, verdict: "Skill rest is too short", advice: "Rest 2-3 min between skill sets so the nervous system recovers.", passed: false }
            : { title: "Skill freshness", grade: 10, verdict: "Handstand and planche are trained fresh", advice: "Keep them first in the session. Quality beats duration.", passed: true },
      maxConsecutiveLoad > 5
        ? { title: "Joint frequency", grade: 2, verdict: maxConsecutiveLoad + " push days in a row", advice: "Insert a full rest day before the wrists and elbows complain.", passed: false }
        : maxConsecutiveLoad > 3
          ? { title: "Joint frequency", grade: 5, verdict: maxConsecutiveLoad + " push days in a row", advice: "Keep push and skill blocks to three days, then load a pull or rest day.", passed: false }
          : { title: "Joint frequency", grade: 10, verdict: "Loading blocks stay under four days", advice: "Recovery fits between push blocks. Keep it that way.", passed: true },
      maxDailyPush > 16 || weeklySets > 110
        ? { title: "Daily volume", grade: 3, verdict: maxDailyPush + " push sets on the biggest day", advice: "Cap push at 12 sets per day and trim the weekly total under 90.", passed: false }
        : maxDailyPush > 12
          ? { title: "Daily volume", grade: 6, verdict: maxDailyPush + " push sets on the biggest day", advice: "Remove the extra push sets until the day sits at 12 or fewer.", passed: false }
          : { title: "Daily volume", grade: 10, verdict: "Daily volume is capped at 12 sets", advice: "Add quality reps or load instead of more sets.", passed: true }
    ];

    var freshnessOk = !skillCircuitDays.length && !shortSkillRestDays.length;
    var growthOk = maxDailyPush <= 12 && pushSets >= 8 && pullSets >= 8 && coreSets >= 6;
    var skillOk = skillDays >= 2 && freshnessOk;
    var penalty = risks.reduce(function (total, risk) { return total + (risk.critical ? 16 : 8); }, 0);
    var score = trainingDays === 0 ? 10 : Math.max(10, Math.min(100, 100 - penalty));

    var verdict = {
      score: score,
      headline: trainingDays === 0
        ? "Build a week first."
        : score >= 85
          ? "A real six-month plan."
          : score >= 65
            ? "Good base. Fix the red flags first."
            : score >= 45
              ? "Promising, but not a six-month plan yet."
              : "Rebuild the week before starting.",
      detail: trainingDays === 0
        ? "Mark at least two training days so the coach has real work to read."
        : risks.length === 0
          ? "Fresh skills, capped volume and enough recovery. This week can carry six months."
          : "The plan has " + risks.length + " " + (risks.length === 1 ? "fix" : "fixes") + ". Apply them before the first session, not after the first ache.",
      growth: growthOk
        ? "Muscle growth: yes. Push and pull are inside the 8-20 set window with a 12-set daily ceiling and enough core work."
        : "Muscle growth: limited. Volume, balance or core coverage is outside the range that reliably builds muscle.",
      skills: skillDays === 0
        ? "Skills: no handstand, planche or lever work, so six months has nothing to build."
        : !freshnessOk
          ? "Skills: at risk. Static work is buried in a circuit or rested too briefly."
          : skillDays < 2
            ? "Skills: too little practice. One session per week will not move a handstand or planche."
            : "Skills: yes. Frequent, fresh static practice makes a six-month handstand and planche realistic."
    };

    return {
      rules: rules,
      pros: pros,
      risks: risks,
      verdict: verdict,
      trainingDays: trainingDays,
      weeklySets: weeklySets,
      pushSets: pushSets,
      pullSets: pullSets,
      coreSets: coreSets,
      legSets: legSets,
      skillDays: skillDays,
      maxDailyPush: maxDailyPush,
      maxConsecutiveLoad: maxConsecutiveLoad,
      skillOk: skillOk
    };
  }

  function scoreTone(score) {
    if (score >= 85) return "green";
    if (score >= 65) return "accent";
    if (score >= 45) return "amber";
    return "red";
  }

  function ring(score) {
    var tone = scoreTone(score);
    var color = tone === "green" ? "#22C55E" : tone === "accent" ? "#2F80ED" : tone === "amber" ? "#F59E0B" : "#EF4444";
    var node = h("div", { class: "score-ring" }, [
      h("div", { class: "score-ring__inner" }, [
        h("span", { class: "score-ring__value", text: score }),
        h("span", { class: "score-ring__max", text: "/100" })
      ])
    ]);
    node.style.setProperty("--score", score);
    node.style.setProperty("--ring", color);
    return node;
  }

  function tail(day) {
    return day.planDay.state === "REST" ? "Rest day" : day.planDay.state === "OPTIONAL" ? "Optional session" : "Training day";
  }

  function nextState(state) {
    return state === "REST" ? "TRAINING" : state === "TRAINING" ? "OPTIONAL" : "REST";
  }

  SM.route("edit-plan", function (container) {
    var plan = SM.planSnapshot();
    var trainingDays = plan.trainingDays.length;
    var totalTrainings = plan.days.reduce(function (sum, day) { return sum + day.totalTrainings; }, 0);
    var totalSets = plan.days.reduce(function (sum, day) { return sum + day.totalSets; }, 0);

    var rows = plan.days.map(function (day) {
      var state = day.planDay.state;
      var tone = state === "TRAINING" ? "pill--blue" : state === "OPTIONAL" ? "pill--orange" : "pill--muted";
      var stateButton = h("button", {
        class: "pill " + tone + " plan-state",
        type: "button",
        on: {
          click: function (event) {
            event.stopPropagation();
            SM.dayRow(day.planDay.dayOfWeek).state = nextState(SM.dayRow(day.planDay.dayOfWeek).state);
            SM.commit(true);
          }
        }
      }, [state === "REST" ? "REST" : state]);

      var entryLines = day.entries.map(function (entry) {
        return h("div", { class: "row--between plan-entry" }, [
          h("span", { class: "body", text: entry.name }),
          h("span", { class: "small dim", text: SM.badgeText(entry.sets, entry.reps, entry.unit) })
        ]);
      });

      var card = h("div", {
        class: "card day-card",
        role: "button",
        tabindex: "0",
        on: {
          click: function () { SM.go("day-editor/" + day.planDay.dayOfWeek); },
          keydown: function (event) {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              SM.go("day-editor/" + day.planDay.dayOfWeek);
            }
          }
        }
      }, [
        h("div", { class: "row--between" }, [
          h("span", { class: "title-md", text: SM.weekFull[day.planDay.dayOfWeek - 1] }),
          stateButton
        ]),
        h("span", { class: "small dim", text: day.planDay.name || tail(day) }),
        entryLines.length ? h("div", { class: "stack--xs plan-entries" }, entryLines) : h("span", { class: "small muted", text: "No exercises" }),
        entryLines.length ? h("span", { class: "label dim", text: day.totalTrainings + " trainings · " + day.totalSets + " sets" }) : null
      ]);
      return card;
    });

    container.appendChild(h("div", { class: "screen plan-page" }, [
      SM.ui.screenHead({ title: "Edit plan", back: function () { SM.back(); } }),
      SM.ui.card([
        h("p", { class: "label accent", text: "YOUR WEEK" }),
        h("p", {
          class: "title-md",
          text: trainingDays + " training " + (trainingDays === 1 ? "day" : "days") + " · " + totalTrainings + " trainings · " + totalSets + " sets"
        })
      ]),
      h("div", { class: "stack" }, rows),
      SM.ui.button("Analyze plan", function () { SM.go("plan-analysis"); }, { icon: "stats" }),
      SM.ui.button("Done", function () { SM.back(); }, { variant: "ghost" })
    ]));
  });

  SM.route("plan-analysis", function (container) {
    var body = h("div", { class: "stack" }, []);
    var page = h("div", { class: "screen plan-page" }, [
      SM.ui.screenHead({ title: "Plan analysis", back: function () { SM.back(); } }),
      body
    ]);
    container.appendChild(page);

    var stages = ["Skill freshness", "Joint frequency", "Daily volume", "Verdict"];
    var stage = 0;
    var timer = null;

    function drawLoading() {
      body.innerHTML = "";
      body.appendChild(h("div", { class: "analysis-loading" }, [
        h("div", { class: "analysis-loading__ring" }, [h("span", { class: "display", text: Math.round(((stage + 1) / 4) * 100) + "%" })]),
        h("p", { class: "label accent", text: "ANALYZING THE WEEK" }),
        SM.ui.progressBar((stage + 1) / 4),
        h("div", { class: "stack--sm" }, stages.map(function (label, index) {
          return h("div", { class: "row analysis-stage " + (index < stage ? "is-done" : index === stage ? "is-current" : "is-waiting") }, [
            h("span", { class: "analysis-stage__dot", text: index < stage ? "•" : String(index + 1) }),
            h("span", { class: "small", text: label })
          ]);
        }))
      ]));
    }

    function finish() {
      window.clearInterval(timer);
      var report = audit(SM.planSnapshot());
      drawReport(report);
    }

    function drawReport(report) {
      body.innerHTML = "";
      var tone = scoreTone(report.verdict.score);

      var heroStats = [
        { label: "PUSH/DAY", value: String(report.maxDailyPush) },
        { label: "SKILL DAYS", value: String(report.skillDays) },
        { label: "LONGEST RUN", value: String(report.maxConsecutiveLoad) }
      ];

      var hero = SM.ui.card([
        h("div", { class: "row row--top analysis-hero" }, [
          h("div", { class: "grow stack--xs" }, [
            h("p", { class: "label accent", text: "SIX-MONTH READINESS" }),
            h("p", { class: "title-md", text: report.verdict.headline }),
            h("p", { class: "small muted", text: report.verdict.detail })
          ]),
          ring(report.verdict.score)
        ]),
        h("div", { class: "row analysis-hero-stats" }, heroStats.map(function (stat) {
          return h("div", { class: "grow stack--xs" }, [
            h("span", { class: "title-md", text: stat.value }),
            h("span", { class: "label dim", text: stat.label })
          ]);
        })),
        h("p", {
          class: "label dim",
          text: report.trainingDays + " training days · " + report.weeklySets + " weekly sets · " + report.pushSets + " push / " + report.pullSets + " pull"
        }),
        h("p", { class: "label " + tone, text: "Readiness " + report.verdict.score + "/100" })
      ]);

      var ruleCards = report.rules.map(function (rule) {
        var ruleTone = scoreTone(rule.grade * 10);
        var grade = h("span", { class: "grade " + ruleTone, text: rule.grade + "/10" });
        return SM.ui.card([
          h("div", { class: "row--between" }, [
            h("div", { class: "grow stack--xs" }, [
              h("span", { class: "title-sm", text: rule.title }),
              h("span", { class: "small muted", text: rule.verdict })
            ]),
            grade
          ]),
          h("p", { class: "small " + (rule.passed ? "muted" : ruleTone), text: rule.advice })
        ]);
      });

      var prosCard = SM.ui.card(report.pros.map(function (point) {
        return h("div", { class: "row row--top" }, [
          h("span", { class: "tick" }, [SM.ui.svg("check")]),
          h("div", { class: "grow stack--xs" }, [
            h("span", { class: "title-sm", text: point.title }),
            h("span", { class: "small muted", text: point.detail })
          ])
        ]);
      }));

      var riskNodes = report.risks.length ? report.risks.map(function (point) {
        return SM.ui.card([
          h("div", { class: "row row--top" }, [
            h("span", { class: "warn" }, [h("span", { text: "!" })]),
            h("div", { class: "grow stack--xs" }, [
              h("div", { class: "row" }, [
                h("span", { class: "title-sm grow", text: point.title }),
                point.critical ? h("span", { class: "pill pill--amber", text: "FIX FIRST" }) : null
              ]),
              h("span", { class: "small muted", text: point.detail })
            ])
          ])
        ]);
      }) : [SM.ui.card([h("div", { class: "row" }, [h("span", { class: "tick" }, [SM.ui.svg("check")]), h("span", { class: "title-sm", text: "No red flags" })])])];

      var verdictCard = h("div", { class: "card verdict " + tone }, [
        h("div", { class: "row--between" }, [
          h("span", { class: "label " + tone, text: "VERDICT" }),
          h("span", { class: "small " + tone, text: report.verdict.score + "/100" })
        ]),
        h("span", { class: "title-lg", text: report.verdict.headline }),
        h("span", { class: "small muted", text: report.verdict.detail }),
        h("span", { class: "small", text: report.verdict.growth }),
        h("span", { class: "small", text: report.verdict.skills })
      ]);

      var improve = SM.ui.button("Improve Plan", function () {
        var fix = buildFix(SM.planSnapshot());
        SM.commit(false);
        improve.disabled = true;
        var overlay = SM.ui.overlay(h("div", { class: "card success-card" }, [
          h("div", { class: "tick tick--big" }, [SM.ui.svg("check")]),
          h("span", { class: "title-lg center", text: "Plan improved" }),
          h("div", { class: "stack--xs" }, fix.summary.map(function (line) {
            return h("span", { class: "small muted center", text: "• " + line });
          }))
        ]), true);
        window.setTimeout(function () {
          overlay.close();
          SM.go("edit-plan");
        }, 2200);
      }, { icon: "trophy" });

      body.appendChild(h("div", { class: "stack" }, [
        hero,
        h("p", { class: "label accent", text: "CHECKS" }),
        h("div", { class: "stack" }, ruleCards),
        h("p", { class: "label accent", text: "PROS" }),
        prosCard,
        h("p", { class: "label accent", text: "RISKS" }),
        h("div", { class: "stack" }, riskNodes),
        h("p", { class: "label accent", text: "VERDICT" }),
        verdictCard,
        improve,
        SM.ui.button("Back to plan", function () { SM.back(); }, { variant: "ghost" })
      ]));
    }

    drawLoading();
    timer = window.setInterval(function () {
      if (!document.body.contains(page)) {
        window.clearInterval(timer);
        return;
      }
      stage += 1;
      if (stage >= 3) {
        finish();
      } else {
        drawLoading();
      }
    }, 750);
  });

  function buildFix(plan) {
    var movedSkills = 0;
    var cappedDays = 0;
    var updates = [];

    plan.days.forEach(function (day) {
      var rows = day.entries.slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; });
      if (day.planDay.state === "REST" || !rows.length) return;

      var skills = rows.filter(isStaticSkill);
      var others = rows.filter(function (row) { return !isStaticSkill(row); });
      var skillsWereLeading = rows.slice(0, skills.length).every(isStaticSkill);
      var skillsNeededRest = skills.some(function (row) { return row.restSeconds < 180; });

      var ordered = skills.map(function (row) {
        return { row: row, rest: Math.max(row.restSeconds, 180) };
      }).concat(others.map(function (row) {
        return { row: row, rest: row.restSeconds };
      }));

      var used = {};
      var kept = [];
      var cappedThisDay = 0;

      ordered.forEach(function (item) {
        var lane = laneOf(item.row);
        if (!lane) {
          kept.push({ row: item.row, sets: item.row.sets, rest: item.rest });
          return;
        }
        var remaining = 12 - (used[lane] || 0);
        if (remaining <= 0) {
          cappedThisDay += 1;
          return;
        }
        var sets = Math.min(item.row.sets, remaining);
        if (sets !== item.row.sets) cappedThisDay += 1;
        used[lane] = (used[lane] || 0) + sets;
        kept.push({ row: item.row, sets: sets, rest: item.rest });
      });

      if (skills.length && (!skillsWereLeading || skillsNeededRest)) movedSkills += skills.length;
      if (cappedThisDay > 0) cappedDays += 1;

      updates.push({ day: day, kept: kept });
    });

    var allSevenTraining = plan.days.length === 7 && plan.days.every(function (day) {
      return day.planDay.state === "TRAINING";
    });
    var restDay = null;

    if (allSevenTraining) {
      var lowest = plan.days.slice().sort(function (a, b) {
        return a.totalSets - b.totalSets || b.planDay.dayOfWeek - a.planDay.dayOfWeek;
      })[0];
      restDay = lowest ? lowest.planDay.dayOfWeek : null;
    } else {
      var run = pushSkillRunDays(plan);
      if (run.length > 3) {
        var candidate = run.slice().sort(function (a, b) { return a.totalSets - b.totalSets; })[0];
        restDay = candidate ? candidate.planDay.dayOfWeek : null;
      }
    }

    updates.forEach(function (update) {
      var keptIds = update.kept.map(function (item) { return item.row.planExerciseId; });
      SM.state.entries = SM.state.entries.filter(function (entry) {
        return entry.dayId !== update.day.planDay.id || keptIds.indexOf(entry.id) >= 0;
      });
      update.kept.forEach(function (item, index) {
        var entry = SM.state.entries.filter(function (row) { return row.id === item.row.planExerciseId; })[0];
        if (!entry) return;
        entry.sets = item.sets;
        entry.restSeconds = item.rest;
        entry.sortOrder = index;
      });
    });

    if (restDay != null) {
      SM.state.days.forEach(function (day) {
        if (day.dayOfWeek === restDay) day.state = "REST";
      });
    }

    var summary = [];
    if (movedSkills > 0) {
      summary.push("Moved " + movedSkills + " skill " + (movedSkills === 1 ? "exercise" : "exercises") + " to the front with 3 min rest.");
    }
    if (cappedDays > 0) {
      summary.push("Capped daily muscle volume at 12 sets on " + cappedDays + " " + (cappedDays === 1 ? "day" : "days") + ".");
    }
    if (restDay != null) {
      summary.push("Turned " + SM.weekFull[restDay - 1] + " into a full rest day.");
    }
    if (!summary.length) {
      summary.push("No automatic fixes were needed. The week already follows the rules.");
    }

    return { summary: summary };
  }

  function pushSkillRunDays(plan) {
    var byWeekday = [1, 2, 3, 4, 5, 6, 7].map(function (weekday) {
      return plan.days.filter(function (day) { return day.planDay.dayOfWeek === weekday; })[0] || null;
    });
    var loaded = byWeekday.map(function (day) {
      return !!day && day.planDay.state !== "REST" && day.entries.some(function (entry) {
        return laneOf(entry) === "PUSH" || isStaticSkill(entry);
      });
    });
    if (!loaded.some(Boolean)) return [];
    var start = loaded.indexOf(false) + 1;
    var run = 0;
    var bestStart = 0;
    var bestLength = 0;
    for (var offset = 0; offset < 7; offset += 1) {
      var index = (start + offset) % 7;
      if (loaded[index]) {
        if (run === 0) bestStart = index;
        run += 1;
        if (run > bestLength) {
          bestLength = run;
        }
      } else {
        run = 0;
        bestStart = 0;
      }
    }
    if (!bestLength) return [];
    var days = [];
    for (var step = 0; step < bestLength; step += 1) {
      var candidate = byWeekday[(bestStart + step) % 7];
      if (candidate) days.push(candidate);
    }
    return days;
  }

  SM.route("weekly-progression", function (container) {
    var weekStart = SM.completedWeekStart();
    var weekKey = SM.iso(weekStart);
    var resolved = {};
    var message = null;

    container.appendChild(h("div", { class: "screen plan-page", id: "progression-page" }));
    var page = container.lastChild;

    function headline(row) {
      var unit = row.entry.unit === "SECONDS" ? "s" : "";
      if (row.action === "INCREASE") return "Increase to " + row.suggestedSets + " × " + row.suggestedReps + unit;
      if (row.action === "REGRESS") return "Regress to " + row.suggestedSets + " × " + row.suggestedReps + unit;
      return "Keep " + row.entry.sets + " × " + row.entry.reps + unit;
    }

    function detail(row) {
      var unit = row.entry.unit === "SECONDS" ? "s" : "";
      if (row.action === "INCREASE") return "All " + row.plannedSets + " sets were completed cleanly; the last set hit the target.";
      if (row.action === "KEEP") return "Completed " + row.completedSets + " of " + row.plannedSets + " sets, last set " + row.lastSetReps + unit + ". Hold this step.";
      return "No set was logged for this training. One step back keeps the session doable.";
    }

    function resolve(row, accept) {
      if (accept && row.action !== "KEEP") {
        var entry = SM.state.entries.filter(function (item) { return item.id === row.entry.planExerciseId; })[0];
        if (entry) {
          entry.sets = row.suggestedSets;
          entry.reps = row.suggestedReps;
        }
        message = row.entry.name + ": " + headline(row).toLowerCase();
      } else {
        message = row.entry.name + ": kept as is.";
      }
      resolved[row.entry.planExerciseId] = true;
      SM.commit(false);
      draw();
    }

    function draw() {
      var rows = SM.weeklyProgression(weekStart);
      var open = rows.filter(function (row) { return !resolved[row.entry.planExerciseId]; });
      var allResolved = rows.length > 0 && open.length === 0;

      if (allResolved && SM.state.settings.lastProgressionReviewWeek !== weekKey) {
        SM.state.settings.lastProgressionReviewWeek = weekKey;
        SM.commit(false);
      }

      page.innerHTML = "";
      page.appendChild(SM.ui.screenHead({
        title: "Weekly progression",
        subtitle: SM.formatShort(weekStart) + " to " + SM.formatShort(SM.addDays(weekStart, 6)),
        back: function () { SM.back(); }
      }));

      if (message) {
        page.appendChild(h("p", { class: "label green", text: message }));
      }

      if (!rows.length) {
        page.appendChild(SM.ui.card([
          h("span", { class: "title-md", text: "No completed sessions last week" })
        ]));
      } else {
        if (allResolved) {
          page.appendChild(SM.ui.card([
            h("span", { class: "title-md green", text: "Week reviewed" })
          ]));
        }

        open.forEach(function (row) {
          var unit = row.entry.unit === "SECONDS" ? "s" : "";
          page.appendChild(SM.ui.card([
            h("div", { class: "row--between row--top" }, [
              h("div", { class: "grow stack--xs" }, [
                h("span", { class: "title-md", text: row.entry.name }),
                h("span", {
                  class: "label dim",
                  text: SM.weekShort[row.dayOfWeek - 1] + " · " + row.entry.label + " · now " + row.entry.sets + " × " + row.entry.reps + unit
                })
              ]),
              h("span", {
                class: "small " + (row.completedSets >= row.plannedSets ? "green" : "amber"),
                text: row.completedSets + "/" + row.plannedSets + " sets"
              })
            ]),
            h("span", { class: "body accent", text: headline(row) }),
            h("span", { class: "small muted", text: detail(row) }),
            h("div", { class: "row row--wrap progression-actions" }, [
              SM.ui.textButton("Keep", function () { resolve(row, false); }, "muted"),
              row.action !== "KEEP" ? SM.ui.textButton("Accept", function () { resolve(row, true); }) : null
            ])
          ]));
        });
      }

      if (allResolved) {
        page.appendChild(SM.ui.button("Done", function () { SM.back(); }));
      } else {
        page.appendChild(SM.ui.button("Decide later", function () { SM.back(); }, { variant: "ghost" }));
      }
    }

    draw();
  });
})();
