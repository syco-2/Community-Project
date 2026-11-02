(function () {
  var KEY = "sixmonths.v1";
  var WEEK_SHORT = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  var WEEK_FULL = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
  var CATEGORIES = ["PUSH", "PULL", "SKILL", "CORE", "MOBILITY", "LEGS", "OTHER"];
  var CATEGORY_NAMES = {
    PUSH: "Push",
    PULL: "Pull",
    SKILL: "Skill",
    CORE: "Core",
    MOBILITY: "Mobility",
    LEGS: "Legs",
    OTHER: "Other"
  };
  var FREEZE_TOKENS = 2;
  var CYCLE_DAYS = 182;
  var PHASE_DAYS = 42;

  function pad(value) {
    return value < 10 ? "0" + value : "" + value;
  }

  function iso(date) {
    return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
  }

  function parseIso(value) {
    var match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
    if (!match) return null;
    return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  }

  function today() {
    var now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  function addDays(date, days) {
    var copy = new Date(date.getTime());
    copy.setDate(copy.getDate() + days);
    return copy;
  }

  function weekday(date) {
    return ((date.getDay() + 6) % 7) + 1;
  }

  function startOfWeek(date) {
    return addDays(date, 1 - weekday(date));
  }

  function relativeLabel(date) {
    var base = today();
    if (iso(date) === iso(base)) return "Today";
    if (iso(date) === iso(addDays(base, 1))) return "Tomorrow";
    return WEEK_FULL[weekday(date) - 1] + ", " + formatShort(date);
  }

  function formatShort(date) {
    var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    return WEEK_SHORT[weekday(date) - 1] + ", " + months[date.getMonth()] + " " + date.getDate();
  }

  function formatClock(millis) {
    var date = new Date(millis);
    return pad(date.getHours()) + ":" + pad(date.getMinutes());
  }

  function formatTime(seconds) {
    var safe = Math.max(0, seconds || 0);
    return Math.floor(safe / 60) + ":" + pad(safe % 60);
  }

  function formatDuration(seconds) {
    var minutes = Math.floor(seconds / 60);
    var rest = seconds % 60;
    return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
  }

  function nextOccurrence(dayOfWeek) {
    return addDays(today(), ((dayOfWeek - weekday(today())) + 7) % 7);
  }

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 9);
  }

  function defaults() {
    return {
      settings: {
        onboardingCompleted: false,
        onboardingStep: 1,
        reminderTime: "19:00",
        reminderDays: [],
        planPhase: 1,
        planPhaseStartedAt: 0,
        cycleStartedAt: 0,
        cycleIsExplicit: false,
        animationsEnabled: true,
        sfxEnabled: true,
        musicWithWorkout: true,
        futureMessage: "",
        futureMessageRevealed: false,
        lastProgressionReviewWeek: null,
        lastTrophyWeek: null
      },
      days: [],
      exercises: [],
      entries: [],
      sessions: [],
      setLogs: [],
      monsters: [],
      music: [],
      streak: {
        currentStreak: 0,
        longestStreak: 0,
        lastCompletedDate: null,
        lastEvaluatedDate: null,
        freezeTokens: FREEZE_TOKENS,
        totalCompletedSessions: 0
      }
    };
  }

  function merge(base, stored) {
    if (!stored || typeof stored !== "object") return base;
    Object.keys(stored).forEach(function (key) {
      if (Array.isArray(stored[key])) {
        base[key] = stored[key];
      } else if (stored[key] && typeof stored[key] === "object") {
        base[key] = merge(base[key] || {}, stored[key]);
      } else if (stored[key] !== undefined) {
        base[key] = stored[key];
      }
    });
    return base;
  }

  function load() {
    var stored = null;
    try {
      stored = JSON.parse(window.localStorage.getItem(KEY) || "null");
    } catch (error) {
      stored = null;
    }
    return merge(defaults(), stored);
  }

  var state = load();

  function save() {
    try {
      window.localStorage.setItem(KEY, JSON.stringify(state));
    } catch (error) {
      return false;
    }
    return true;
  }

  function ensureWeek() {
    var changed = false;
    for (var dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek += 1) {
      if (!state.days.some(function (day) { return day.dayOfWeek === dayOfWeek; })) {
        state.days.push({ id: uid(), dayOfWeek: dayOfWeek, state: "REST", name: null });
        changed = true;
      }
    }
    if (changed) save();
    return changed;
  }

  function dayRow(dayOfWeek) {
    ensureWeek();
    return state.days.filter(function (day) { return day.dayOfWeek === dayOfWeek; })[0];
  }

  function exerciseById(id) {
    return state.exercises.filter(function (exercise) { return exercise.id === id; })[0] || null;
  }

  function entryView(entry) {
    var exercise = exerciseById(entry.exerciseId) || {};
    return {
      planExerciseId: entry.id,
      dayId: entry.dayId,
      exerciseId: entry.exerciseId,
      name: exercise.name || "",
      category: exercise.category || "OTHER",
      unit: exercise.unit || "REPS",
      notes: exercise.notes || null,
      formTip: exercise.formTip || null,
      label: entry.label,
      sets: entry.sets,
      reps: entry.reps,
      restSeconds: entry.restSeconds || 0,
      sortOrder: entry.sortOrder
    };
  }

  function entriesFor(dayOfWeek) {
    var day = dayRow(dayOfWeek);
    if (!day) return [];
    return state.entries
      .filter(function (entry) { return entry.dayId === day.id; })
      .sort(function (a, b) { return a.sortOrder - b.sortOrder; })
      .map(entryView);
  }

  function allEntries() {
    return state.entries.slice().sort(function (a, b) { return a.sortOrder - b.sortOrder; }).map(entryView);
  }

  function daySnapshot(dayOfWeek) {
    var day = dayRow(dayOfWeek);
    var entries = entriesFor(dayOfWeek);
    return {
      planDay: day,
      entries: entries,
      totalTrainings: entries.length,
      totalSets: entries.reduce(function (sum, entry) { return sum + entry.sets; }, 0)
    };
  }

  function planSnapshot() {
    var days = [];
    for (var dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek += 1) {
      days.push(daySnapshot(dayOfWeek));
    }
    return {
      days: days,
      trainingDays: days.filter(function (day) { return day.planDay.state === "TRAINING"; }),
      optionalDays: days.filter(function (day) { return day.planDay.state === "OPTIONAL"; })
    };
  }

  function completedSessions() {
    return state.sessions.filter(function (session) { return session.status === "COMPLETED"; });
  }

  function sessionDate(session) {
    return iso(new Date(session.finishedAt || session.startedAt));
  }

  function sessionsInRange(start, end) {
    return completedSessions().filter(function (session) {
      var date = sessionDate(session);
      return date >= iso(start) && date <= iso(end);
    });
  }

  function completedDates() {
    var cycleStart = state.settings.cycleStartedAt || 0;
    var dates = {};
    completedSessions().forEach(function (session) {
      if (session.startedAt < cycleStart) return;
      dates[sessionDate(session)] = true;
    });
    return dates;
  }

  function evaluateStreak() {
    var base = today();
    var trainingWeekdays = state.days
      .filter(function (day) { return day.state === "TRAINING"; })
      .map(function (day) { return day.dayOfWeek; });

    if (!trainingWeekdays.length) {
      save();
      return { state: state.streak, missed: 0, frozen: 0, hasTrainingDays: false };
    }

    var done = completedDates();
    var current = state.streak;
    var start = parseIso(current.lastEvaluatedDate) || parseIso(current.lastCompletedDate) || addDays(base, -1);
    var cursor = addDays(start, 1);
    var missed = 0;
    var frozen = 0;

    while (cursor.getTime() < base.getTime()) {
      if (trainingWeekdays.indexOf(weekday(cursor)) >= 0) {
        if (done[iso(cursor)]) {
          if (current.lastCompletedDate !== iso(cursor)) {
            current.currentStreak += 1;
            current.longestStreak = Math.max(current.longestStreak, current.currentStreak);
            current.lastCompletedDate = iso(cursor);
          }
        } else {
          missed += 1;
          if (current.freezeTokens > 0) {
            frozen += 1;
            current.freezeTokens -= 1;
          } else {
            current.currentStreak = 0;
          }
        }
      }
      current.lastEvaluatedDate = iso(cursor);
      cursor = addDays(cursor, 1);
    }

    if (trainingWeekdays.indexOf(weekday(base)) >= 0 && done[iso(base)] && current.lastCompletedDate !== iso(base)) {
      current.currentStreak += 1;
      current.longestStreak = Math.max(current.longestStreak, current.currentStreak);
      current.lastCompletedDate = iso(base);
    }

    current.totalCompletedSessions = completedSessions().length;
    state.streak = current;
    save();
    return { state: current, missed: missed, frozen: frozen, hasTrainingDays: true };
  }

  function resetForNewCycle() {
    state.streak.currentStreak = 0;
    state.streak.lastCompletedDate = null;
    state.streak.lastEvaluatedDate = iso(addDays(today(), -1));
    save();
  }

  function activeSession(planDayId) {
    return state.sessions.filter(function (session) {
      return session.status === "ACTIVE" && (planDayId ? session.planDayId === planDayId : true);
    })[0] || null;
  }

  function startSession(planDayId) {
    var existing = activeSession(planDayId);
    if (existing) return existing;
    var session = {
      id: uid(),
      planDayId: planDayId || null,
      startedAt: Date.now(),
      finishedAt: null,
      status: "ACTIVE",
      durationSeconds: null
    };
    state.sessions.push(session);
    save();
    return session;
  }

  function sessionById(id) {
    return state.sessions.filter(function (session) { return session.id === id; })[0] || null;
  }

  function logsFor(sessionId) {
    return state.setLogs.filter(function (log) { return log.sessionId === sessionId; });
  }

  function logSet(sessionId, entry, setIndex, repsCompleted) {
    state.setLogs.push({
      id: uid(),
      sessionId: sessionId,
      exerciseId: entry.exerciseId,
      planExerciseId: entry.planExerciseId,
      setIndex: setIndex,
      repsCompleted: repsCompleted,
      completedAt: Date.now()
    });
    save();
  }

  function completeSession(sessionId) {
    var session = sessionById(sessionId);
    if (!session) return null;
    var finished = Date.now();
    session.status = "COMPLETED";
    session.finishedAt = finished;
    session.durationSeconds = Math.round((finished - session.startedAt) / 1000);
    save();
    evaluateStreak();
    return session;
  }

  function abandonSession(sessionId) {
    var session = sessionById(sessionId);
    if (!session) return;
    session.status = "ABANDONED";
    session.finishedAt = Date.now();
    save();
  }

  function normalizeName(raw) {
    return (raw || "").toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, " ")
      .trim()
      .replace(/ +/g, " ");
  }

  var PLANCHE_WORDS = ["planche", "protraction", "protract", "maltese"];
  var SKILL_WORDS = ["handstand", "lever", "human flag", "muscle up", "muscleup", "elbow lever", "l sit", "v sit", "press to handstand", "skin the cat", "one arm"];

  function blockOf(name, category) {
    var normalized = normalizeName(name);
    if (PLANCHE_WORDS.some(function (word) { return normalized.indexOf(word) >= 0; })) return "PLANCHE";
    if (category === "SKILL") return "SKILL";
    if (SKILL_WORDS.some(function (word) { return normalized.indexOf(word) >= 0; })) return "SKILL";
    return "NORMAL";
  }

  function circuitSequence(entries) {
    var steps = [];
    ["SKILL", "PLANCHE"].forEach(function (block) {
      entries
        .filter(function (entry) { return blockOf(entry.name, entry.category) === block; })
        .sort(function (a, b) { return a.sortOrder - b.sortOrder; })
        .forEach(function (entry) {
          for (var set = 0; set < entry.sets; set += 1) {
            steps.push({ entry: entry, setIndex: set, block: block });
          }
        });
    });

    var normal = entries
      .filter(function (entry) { return blockOf(entry.name, entry.category) === "NORMAL"; })
      .sort(function (a, b) { return a.sortOrder - b.sortOrder; });
    var rounds = normal.reduce(function (max, entry) { return Math.max(max, entry.sets); }, 0);
    for (var round = 0; round < rounds; round += 1) {
      normal.forEach(function (entry) {
        if (round < entry.sets) {
          steps.push({ entry: entry, setIndex: round, block: "NORMAL" });
        }
      });
    }
    return steps;
  }

  function stepKey(step) {
    return step.entry.planExerciseId + "|" + step.setIndex;
  }

  function loggedKeys(sessionId) {
    var steps = circuitStepsForSession(sessionId);
    var result = {};
    logsFor(sessionId).forEach(function (log) {
      var planExerciseId = log.planExerciseId;
      if (!planExerciseId) {
        var match = steps.filter(function (step) {
          return step.setIndex === log.setIndex && step.entry.exerciseId === log.exerciseId;
        })[0];
        planExerciseId = match ? match.entry.planExerciseId : "";
      }
      if (planExerciseId) result[planExerciseId + "|" + log.setIndex] = true;
    });
    return result;
  }

  function circuitStepsForSession(sessionId) {
    var session = sessionById(sessionId);
    if (!session || !session.planDayId) return [];
    var day = state.days.filter(function (row) { return row.id === session.planDayId; })[0];
    if (!day) return [];
    return circuitSequence(entriesFor(day.dayOfWeek));
  }

  function blockLabel(block) {
    return block === "SKILL" ? "Skill" : block === "PLANCHE" ? "Planche" : "Training";
  }

  function firstOpenStep(steps, logged) {
    for (var index = 0; index < steps.length; index += 1) {
      if (!logged[stepKey(steps[index])]) return index;
    }
    return -1;
  }

  function circuitRounds(steps) {
    if (!steps.length) return 1;
    return steps.reduce(function (max, step) { return Math.max(max, step.setIndex); }, 0) + 1;
  }

  function categoryName(category) {
    return CATEGORY_NAMES[category] || "Other";
  }

  function suggestCategory(name) {
    var normalized = normalizeName(name);
    if (!normalized) return null;
    function any(words) {
      return words.some(function (word) { return normalized.indexOf(word) >= 0; });
    }
    if (any(["pull up", "chin up", "row", "curl", "face pull"])) return "PULL";
    if (any(["push up", "dip", "press", "pike", "hspu"])) return "PUSH";
    if (any(["planche", "handstand", "lever", "muscle up", "human flag", "frog stand"])) return "SKILL";
    if (any(["plank", "hollow", "leg raise", "crunch", "l sit", "sit up", "ab wheel"])) return "CORE";
    if (any(["stretch", "mobility", "rotation", "shoulder dislocate"])) return "MOBILITY";
    if (any(["squat", "lunge", "calf", "pistol", "nordic", "glute bridge"])) return "LEGS";
    return null;
  }

  function badgeText(sets, reps, unit) {
    return sets + " × " + (unit === "SECONDS" ? reps + "s" : reps);
  }

  function setsByCategory(entries) {
    var result = {};
    CATEGORIES.forEach(function (category) { result[category] = 0; });
    entries.forEach(function (entry) {
      result[entry.category] = (result[entry.category] || 0) + entry.sets;
    });
    return result;
  }

  function coachReview(snapshot) {
    var entries = snapshot.trainingDays.reduce(function (list, day) { return list.concat(day.entries); }, []);
    var sets = setsByCategory(entries);
    var suggestions = [];
    var skillDays = snapshot.trainingDays.filter(function (day) {
      return day.entries.some(function (entry) { return entry.category === "SKILL"; });
    });

    if (skillDays.some(function (day) {
      return day.entries.some(function (entry) {
        return entry.category === "SKILL" && day.entries.indexOf(entry) > 0;
      });
    })) {
      suggestions.push({
        severity: "WARNING",
        title: "Skill first",
        detail: "Put handstand and planche work at the start of the session, before the circuit."
      });
    }

    if (skillDays.length === 0 && entries.length > 0) {
      suggestions.push({
        severity: "INFO",
        title: "No skill work",
        detail: "Add one skill exercise so the week keeps a strength focus."
      });
    }

    var streak = 0;
    snapshot.days.forEach(function (day) {
      if (day.planDay.state === "TRAINING") streak += 1;
      else streak = 0;
    });
    if (streak > 3) {
      suggestions.push({
        severity: "WARNING",
        title: "Consecutive load",
        detail: "More than three training days in a row without rest."
      });
    }

    if (sets.PUSH > 12 || sets.PULL > 12) {
      suggestions.push({
        severity: "CRITICAL",
        title: "Volume high",
        detail: "More than 12 push or pull sets on one day. Cap the volume."
      });
    }

    if (suggestions.length === 0) {
      suggestions.push({
        severity: "INFO",
        title: "Plan is balanced",
        detail: "No obvious imbalance found."
      });
    }

    return { setsByCategory: sets, suggestions: suggestions };
  }

  function progressionDue() {
    if (!state.settings.onboardingCompleted) return false;
    if (!state.settings.planPhaseStartedAt) return false;
    return Date.now() - state.settings.planPhaseStartedAt >= PHASE_DAYS * 86400000;
  }

  function progressionSuggestion(count) {
    if (count >= 16) {
      return { action: "ADD_SET", title: "Add one set", detail: "You completed " + count + " sessions this phase. Add a set to every entry to keep progressing." };
    }
    if (count >= 8) {
      return { action: "ADD_REP", title: "Add reps or seconds", detail: "You completed " + count + " sessions this phase. Add 1 rep (or 10 seconds) to every entry." };
    }
    return { action: "DELOAD", title: "Deload this phase", detail: "Only " + count + " sessions were completed this phase. Reducing reps by ~20% may help you restart." };
  }

  function cycleUnlockDate() {
    if (!state.settings.planPhaseStartedAt) return null;
    return addDays(new Date(state.settings.planPhaseStartedAt), CYCLE_DAYS);
  }

  function cycleDaysRemaining() {
    var unlock = cycleUnlockDate();
    if (!unlock) return CYCLE_DAYS;
    return Math.max(0, Math.ceil((unlock.getTime() - today().getTime()) / 86400000));
  }

  function cycleUnlocked() {
    var unlock = cycleUnlockDate();
    return unlock ? today().getTime() >= unlock.getTime() : false;
  }

  function completedWeekStart() {
    return addDays(startOfWeek(today()), -7);
  }

  function weeklyProgression(weekStart) {
    var plan = planSnapshot();
    var sessions = completedSessions().filter(function (session) {
      var date = sessionDate(session);
      return date >= iso(weekStart) && date < iso(addDays(weekStart, 7));
    });
    var list = [];
    plan.days.forEach(function (day) {
      day.entries.forEach(function (entry) {
        var session = sessions
          .filter(function (row) { return row.planDayId === day.planDay.id; })
          .sort(function (a, b) { return b.startedAt - a.startedAt; })[0];
        if (!session) return;
        var logs = logsFor(session.id)
          .filter(function (log) { return log.exerciseId === entry.exerciseId; })
          .sort(function (a, b) { return a.setIndex - b.setIndex; });
        var completedSets = logs.length;
        var lastReps = logs.length ? logs[logs.length - 1].repsCompleted : 0;
        var step = entry.unit === "SECONDS" ? 10 : 1;
        var action = completedSets >= entry.sets && lastReps >= entry.reps ? "INCREASE" : completedSets === 0 ? "REGRESS" : "KEEP";
        var suggestedReps = action === "INCREASE"
          ? Math.min(entry.reps + step, entry.unit === "SECONDS" ? 600 : 100)
          : action === "REGRESS"
            ? Math.max(entry.reps - step, entry.unit === "SECONDS" ? 10 : 1)
            : entry.reps;
        list.push({
          entry: entry,
          dayOfWeek: day.planDay.dayOfWeek,
          completedSets: completedSets,
          plannedSets: entry.sets,
          lastSetReps: lastReps,
          action: action,
          suggestedSets: entry.sets,
          suggestedReps: suggestedReps
        });
      });
    });
    return list;
  }

  function nextReminder() {
    var settings = state.settings;
    if (!settings.reminderDays.length) return null;
    for (var offset = 0; offset < 8; offset += 1) {
      var date = addDays(today(), offset);
      if (settings.reminderDays.indexOf(weekday(date)) >= 0) {
        return WEEK_SHORT[weekday(date) - 1] + " · " + settings.reminderTime;
      }
    }
    return null;
  }

  var ICONS = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5.5 9.5V21h13V9.5"/>',
    train: '<path d="M8 4v16"/><path d="M16 4v16"/><path d="M4 8h4v8H4z"/><path d="M16 8h4v8h-4z"/>',
    can: '<path d="M8 3h8v18l-4-2-4 2z"/><path d="M8 7h8"/>',
    music: '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><path d="M4 14h3v6H5a1 1 0 0 1-1-1z"/><path d="M20 14h-3v6h2a1 1 0 0 0 1-1z"/>',
    person: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-6 8-6s8 2 8 6"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    close: '<path d="M6 6l12 12"/><path d="M18 6L6 18"/>',
    plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
    minus: '<path d="M5 12h14"/>',
    check: '<path d="M4 12l5 5L20 6"/>',
    trash: '<path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l1 14h10l1-14"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3"/><path d="M12 19v3"/><path d="M2 12h3"/><path d="M19 12h3"/><path d="M5 5l2 2"/><path d="M17 17l2 2"/><path d="M19 5l-2 2"/><path d="M7 17l-2 2"/>',
    flame: '<path d="M12 3c3 4 5 6 5 9a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3 1-5 1-8z"/>',
    stats: '<path d="M5 20V10"/><path d="M12 20V4"/><path d="M19 20v-7"/>',
    musicNote: '<path d="M9 18V6l10-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/>',
    trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0z"/><path d="M8 6H5v2a3 3 0 0 0 3 3"/><path d="M16 6h3v2a3 3 0 0 1-3 3"/><path d="M12 13v5"/><path d="M9 21h6"/>',
    camera: '<path d="M4 8h3l2-3h6l2 3h3v12H4z"/><circle cx="12" cy="13" r="3.5"/>',
    upload: '<path d="M12 16V4"/><path d="M7 9l5-5 5 5"/><path d="M4 20h16"/>',
    download: '<path d="M12 4v12"/><path d="M7 11l5 5 5-5"/><path d="M4 20h16"/>',
    lock: '<rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
    playCircle: '<circle cx="12" cy="12" r="9"/><path d="M10 8.5l6 3.5-6 3.5z"/>',
    skip: '<path d="M5 5l9 7-9 7z"/><path d="M19 5v14"/>',
    timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l3 2"/><path d="M9 2h6"/>'
  };

  function svg(name) {
    var node = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    node.setAttribute("viewBox", "0 0 24 24");
    node.setAttribute("fill", "none");
    node.setAttribute("stroke", "currentColor");
    node.setAttribute("stroke-width", "1.8");
    node.setAttribute("stroke-linecap", "round");
    node.setAttribute("stroke-linejoin", "round");
    node.innerHTML = ICONS[name] || ICONS.more;
    return node;
  }

  function h(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (key) {
        var value = attrs[key];
        if (key === "class") node.className = value;
        else if (key === "text") node.textContent = value;
        else if (key === "html") node.innerHTML = value;
        else if (key === "on") {
          Object.keys(value).forEach(function (event) { node.addEventListener(event, value[event]); });
        } else if (key === "style" && value && typeof value === "object") {
          Object.keys(value).forEach(function (styleKey) { node.style[styleKey] = value[styleKey]; });
        } else if (value === true) {
          node.setAttribute(key, "");
        } else if (value !== false && value != null) {
          node.setAttribute(key, value);
        }
      });
    }
    (children || []).forEach(function (child) {
      if (child == null || child === false) return;
      if (Array.isArray(child)) {
        child.forEach(function (inner) {
          if (inner == null || inner === false) return;
          node.appendChild(typeof inner === "string" ? document.createTextNode(inner) : inner);
        });
      } else {
        node.appendChild(typeof child === "string" ? document.createTextNode(child) : child);
      }
    });
    return node;
  }

  function card(children, className) {
    return h("div", { class: "card" + (className ? " " + className : "") }, children);
  }

  function stack(children, className) {
    return h("div", { class: "stack" + (className ? " " + className : "") }, children);
  }

  function textButton(label, onClick, className) {
    return h("button", { class: "btn--text" + (className ? " " + className : ""), type: "button", on: { click: onClick } }, [label]);
  }

  function iconButton(iconName, onClick, label) {
    return h("button", { class: "btn--icon", type: "button", "aria-label": label || iconName, on: { click: onClick } }, [svg(iconName)]);
  }

  function button(label, onClick, options) {
    var opts = options || {};
    var node = h("button", {
      class: "btn" + (opts.variant === "ghost" ? " btn--ghost" : "") + (opts.variant === "danger" ? " btn--danger" : ""),
      type: "button",
      disabled: opts.disabled === true,
      on: { click: onClick }
    }, [opts.icon ? svg(opts.icon) : null, label]);
    return node;
  }

  function badge(text) {
    return h("span", { class: "badge", text: text });
  }

  function pill(text, tone) {
    return h("span", { class: "pill" + (tone ? " pill--" + tone : ""), text: text });
  }

  function chip(label, selected, onClick) {
    return h("button", {
      class: "chip" + (selected ? " is-on" : ""),
      type: "button",
      on: { click: onClick }
    }, [label]);
  }

  function field(options) {
    var input = options.multiline
      ? h("textarea", { class: "textarea", value: options.value || "", placeholder: options.placeholder || "" })
      : h("input", { class: "input", type: options.type || "text", value: options.value || "", placeholder: options.placeholder || "" });
    if (options.inputMode) input.setAttribute("inputmode", options.inputMode);
    if (options.onInput) input.addEventListener("input", function (event) { options.onInput(event.target.value, event); });
    return h("label", { class: "field" }, [
      options.label ? h("span", { class: "field__label", text: options.label }) : null,
      input
    ]);
  }

  function dots(step, total) {
    var children = [];
    for (var index = 1; index <= total; index += 1) {
      var cls = index === step ? "dot is-active" : index < step ? "dot is-done" : "dot";
      children.push(h("span", { class: cls }));
    }
    return h("div", { class: "dots" }, children);
  }

  function progressBar(value) {
    return h("div", { class: "progress" }, [
      h("div", { class: "progress__fill", style: { width: Math.round(Math.max(0, Math.min(1, value)) * 100) + "%" } })
    ]);
  }

  function ring(completed, planned, size) {
    var dimension = size || 76;
    var radius = dimension / 2 - 5;
    var circumference = 2 * Math.PI * radius;
    var ratio = planned > 0 ? Math.min(1, completed / planned) : 0;
    var wrapper = h("div", { class: "row", style: { width: dimension + "px", height: dimension + "px", position: "relative" } });
    wrapper.innerHTML =
      '<svg width="' + dimension + '" height="' + dimension + '" viewBox="0 0 ' + dimension + " " + dimension + '" style="transform:rotate(-90deg)">' +
      '<circle cx="' + dimension / 2 + '" cy="' + dimension / 2 + '" r="' + radius + '" stroke="#1C2633" stroke-width="8" fill="none"/>' +
      '<circle cx="' + dimension / 2 + '" cy="' + dimension / 2 + '" r="' + radius + '" stroke="#2F80ED" stroke-width="8" fill="none" stroke-linecap="round" stroke-dasharray="' + circumference + '" stroke-dashoffset="' + (circumference * (1 - ratio)) + '"/>' +
      "</svg>";
    wrapper.appendChild(h("span", {
      class: "small",
      text: planned > 0 ? completed + "/" + planned : "0",
      style: { position: "absolute", inset: "0", display: "flex", alignItems: "center", justifyContent: "center" }
    }));
    return wrapper;
  }

  function emptyState(title, message) {
    return h("div", { class: "empty" }, [
      svg("train"),
      h("span", { class: "body", text: title }),
      message ? h("span", { class: "small dim", text: message }) : null
    ]);
  }

  function toast(message) {
    var root = document.getElementById("toast-root");
    if (!root) return;
    var node = h("div", { class: "toast", text: message });
    root.appendChild(node);
    window.setTimeout(function () {
      node.remove();
    }, 2200);
  }

  function overlay(content, center) {
    var root = document.getElementById("overlay-root");
    if (!root) return { close: function () {} };
    var layer = h("div", { class: "overlay" + (center ? " overlay--center" : "") }, [content]);
    function close() {
      layer.remove();
    }
    layer.addEventListener("click", function (event) {
      if (event.target === layer) close();
    });
    root.appendChild(layer);
    return { close: close };
  }

  function sheet(options) {
    var panel = h("div", { class: "sheet" }, [
      h("div", { class: "row--between" }, [
        h("h2", { class: "title-lg", text: options.title || "" }),
        iconButton("close", function () { handle.close(); }, "Close")
      ]),
      h("div", { class: "stack", style: { marginTop: "12px" } }, options.body || [])
    ]);
    var handle = overlay(panel, false);
    return handle;
  }

  function dialog(options) {
    var panel = h("div", { class: "dialog" }, [
      h("h2", { class: "title-lg", text: options.title || "" }),
      options.text ? h("p", { class: "body muted", style: { marginTop: "8px" }, text: options.text }) : null,
      h("div", { class: "stack", style: { marginTop: "16px" } }, [
        button(options.confirmLabel || "OK", function () {
          close();
          if (options.onConfirm) options.onConfirm();
        }),
        button(options.cancelLabel || "Cancel", function () { close(); }, { variant: "ghost" })
      ])
    ]);
    var handle = overlay(panel, true);
    function close() {
      handle.close();
    }
    return { close: close };
  }

  function confirmDialog(options) {
    return dialog({
      title: options.title,
      text: options.text,
      confirmLabel: options.confirmLabel || "Delete",
      cancelLabel: "Cancel",
      onConfirm: options.onConfirm
    });
  }

  function screenHead(options) {
    return h("div", { class: "screen-top" }, [
      options.back ? iconButton("back", options.back, "Back") : null,
      h("div", { class: "screen-top__text" }, [
        h("h1", { class: "title-xl", text: options.title || "" }),
        options.subtitle ? h("p", { class: "label dim", text: options.subtitle }) : null,
        options.progress ? h("div", { style: { marginTop: "8px" } }, [dots(options.progress[0], options.progress[1])]) : null
      ]),
      options.actions || null
    ]);
  }

  function openDatabase() {
    return new Promise(function (resolve, reject) {
      var request = window.indexedDB.open("sixmonths", 1);
      request.onupgradeneeded = function () {
        request.result.createObjectStore("files");
      };
      request.onsuccess = function () { resolve(request.result); };
      request.onerror = function () { reject(request.error); };
    });
  }

  function withStore(mode, callback) {
    return openDatabase().then(function (db) {
      return new Promise(function (resolve, reject) {
        var transaction = db.transaction("files", mode);
        var store = transaction.objectStore("files");
        var request = callback(store);
        request.onsuccess = function () { resolve(request.result); };
        request.onerror = function () { reject(request.error); };
      });
    });
  }

  function filePut(key, value) {
    return withStore("readwrite", function (store) { return store.put(value, key); });
  }

  function fileGet(key) {
    return withStore("readonly", function (store) { return store.get(key); });
  }

  function fileDelete(key) {
    return withStore("readwrite", function (store) { return store.delete(key); });
  }

  function readFile(file, asDataUrl) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(reader.result); };
      reader.onerror = function () { reject(reader.error); };
      if (asDataUrl) reader.readAsDataURL(file);
      else reader.readAsArrayBuffer(file);
    });
  }

  var routes = {};

  function go(path) {
    var target = "#/" + String(path).replace(/^#?\/?/, "");
    if (window.location.hash === target) {
      if (window.SM && window.SM.refresh) window.SM.refresh();
      return;
    }
    window.location.hash = target;
  }

  function back() {
    if (window.history.length > 1) window.history.back();
    else go("dashboard");
  }

  function parseRoute(hash) {
    var raw = (hash || "").replace(/^#\/?/, "");
    var parts = raw.split("/").filter(Boolean);
    if (!parts.length) return { name: "", params: {} };
    if (parts[0] === "day-editor") return { name: "day-editor", params: { dayOfWeek: Number(parts[1] || 1) } };
    if (parts[0] === "workout") return { name: "workout", params: { sessionId: parts[1] || "" } };
    return { name: parts.join("/"), params: {} };
  }

  window.SM = {
    state: state,
    save: save,
    commit: function (rerender) {
      save();
      if (rerender && window.SM.refresh) window.SM.refresh();
    },
    refresh: null,
    routes: routes,
    route: function (name, handler) { routes[name] = handler; },
    go: go,
    back: back,
    parseRoute: parseRoute,
    uid: uid,
    today: today,
    iso: iso,
    parseIso: parseIso,
    addDays: addDays,
    weekday: weekday,
    startOfWeek: startOfWeek,
    relativeLabel: relativeLabel,
    formatShort: formatShort,
    formatClock: formatClock,
    formatTime: formatTime,
    formatDuration: formatDuration,
    nextOccurrence: nextOccurrence,
    weekShort: WEEK_SHORT,
    weekFull: WEEK_FULL,
    categories: CATEGORIES,
    categoryName: categoryName,
    suggestCategory: suggestCategory,
    badgeText: badgeText,
    ensureWeek: ensureWeek,
    dayRow: dayRow,
    entriesFor: entriesFor,
    allEntries: allEntries,
    planSnapshot: planSnapshot,
    daySnapshot: daySnapshot,
    completedSessions: completedSessions,
    sessionsInRange: sessionsInRange,
    evaluateStreak: evaluateStreak,
    resetForNewCycle: resetForNewCycle,
    activeSession: activeSession,
    startSession: startSession,
    sessionById: sessionById,
    logsFor: logsFor,
    logSet: logSet,
    completeSession: completeSession,
    abandonSession: abandonSession,
    circuitSequence: circuitSequence,
    circuitStepsForSession: circuitStepsForSession,
    circuitRounds: circuitRounds,
    loggedKeys: loggedKeys,
    firstOpenStep: firstOpenStep,
    stepKey: stepKey,
    blockOf: blockOf,
    blockLabel: blockLabel,
    setsByCategory: setsByCategory,
    coachReview: coachReview,
    progressionDue: progressionDue,
    progressionSuggestion: progressionSuggestion,
    cycleUnlockDate: cycleUnlockDate,
    cycleDaysRemaining: cycleDaysRemaining,
    cycleUnlocked: cycleUnlocked,
    completedWeekStart: completedWeekStart,
    weeklyProgression: weeklyProgression,
    nextReminder: nextReminder,
    filePut: filePut,
    fileGet: fileGet,
    fileDelete: fileDelete,
    readFile: readFile,
    ui: {
      h: h,
      svg: svg,
      card: card,
      stack: stack,
      button: button,
      textButton: textButton,
      iconButton: iconButton,
      badge: badge,
      pill: pill,
      chip: chip,
      field: field,
      dots: dots,
      progressBar: progressBar,
      ring: ring,
      emptyState: emptyState,
      toast: toast,
      sheet: sheet,
      dialog: dialog,
      confirmDialog: confirmDialog,
      screenHead: screenHead,
      overlay: overlay
    }
  };
})();
