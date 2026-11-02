(function () {
  var ui = SM.ui;
  var h = ui.h;

  function saveEntry(dayOfWeek, existing, data) {
    var day = SM.dayRow(dayOfWeek);
    var entry;
    var exercise;
    if (existing) {
      entry = SM.state.entries.filter(function (row) { return row.id === existing.planExerciseId; })[0];
      exercise = SM.state.exercises.filter(function (row) { return row.id === existing.exerciseId; })[0];
      if (!entry || !exercise) return;
      entry.label = data.label;
      entry.sets = data.sets;
      entry.reps = data.reps;
      exercise.name = data.name;
      exercise.category = data.category;
      exercise.unit = data.unit;
      exercise.notes = data.notes;
      exercise.formTip = data.formTip;
    } else {
      exercise = {
        id: SM.uid(),
        name: data.name,
        category: data.category,
        unit: data.unit,
        notes: data.notes,
        formTip: data.formTip
      };
      SM.state.exercises.push(exercise);
      SM.state.entries.push({
        id: SM.uid(),
        dayId: day.id,
        exerciseId: exercise.id,
        label: data.label,
        sets: data.sets,
        reps: data.reps,
        restSeconds: 0,
        sortOrder: SM.entriesFor(dayOfWeek).length
      });
    }
    SM.commit(true);
  }

  function deleteEntry(dayOfWeek, entry) {
    SM.state.entries = SM.state.entries.filter(function (row) { return row.id !== entry.planExerciseId; });
    var used = SM.state.entries.some(function (row) { return row.exerciseId === entry.exerciseId; });
    if (!used) {
      SM.state.exercises = SM.state.exercises.filter(function (row) { return row.id !== entry.exerciseId; });
    }
    SM.entriesFor(dayOfWeek).forEach(function (row, index) {
      var stored = SM.state.entries.filter(function (item) { return item.id === row.planExerciseId; })[0];
      if (stored) stored.sortOrder = index;
    });
    SM.commit(true);
  }

  function duplicateEntry(dayOfWeek, entry) {
    var exercise = {
      id: SM.uid(),
      name: entry.name,
      category: entry.category,
      unit: entry.unit,
      notes: entry.notes,
      formTip: entry.formTip
    };
    SM.state.exercises.push(exercise);
    SM.state.entries.push({
      id: SM.uid(),
      dayId: SM.dayRow(dayOfWeek).id,
      exerciseId: exercise.id,
      label: entry.label,
      sets: entry.sets,
      reps: entry.reps,
      restSeconds: entry.restSeconds,
      sortOrder: entry.sortOrder + 0.5
    });
    SM.entriesFor(dayOfWeek).forEach(function (row, index) {
      var stored = SM.state.entries.filter(function (item) { return item.id === row.planExerciseId; })[0];
      if (stored) stored.sortOrder = index;
    });
    SM.commit(true);
  }

  function moveEntry(dayOfWeek, entry, direction) {
    var entries = SM.entriesFor(dayOfWeek);
    var index = entries.findIndex(function (row) { return row.planExerciseId === entry.planExerciseId; });
    var target = index + direction;
    if (index < 0 || target < 0 || target >= entries.length) return;
    var a = SM.state.entries.filter(function (row) { return row.id === entry.planExerciseId; })[0];
    var b = SM.state.entries.filter(function (row) { return row.id === entries[target].planExerciseId; })[0];
    var swap = a.sortOrder;
    a.sortOrder = b.sortOrder;
    b.sortOrder = swap;
    SM.commit(true);
  }

  function openActions(dayOfWeek, entry, index, total) {
    var sheet = ui.sheet({
      title: entry.name,
      body: [
        h("div", { class: "label dim", text: entry.label }),
        ui.button("Move up", function () { sheet.close(); moveEntry(dayOfWeek, entry, -1); }, { variant: "ghost", disabled: index === 0 }),
        ui.button("Move down", function () { sheet.close(); moveEntry(dayOfWeek, entry, 1); }, { variant: "ghost", disabled: index === total - 1 }),
        ui.button("Edit", function () { sheet.close(); openEditor(dayOfWeek, entry); }, { variant: "ghost" }),
        ui.button("Duplicate", function () { sheet.close(); duplicateEntry(dayOfWeek, entry); }, { variant: "ghost" }),
        ui.button("Delete", function () {
          sheet.close();
          ui.confirmDialog({
            title: "Delete training",
            text: entry.name,
            onConfirm: function () { deleteEntry(dayOfWeek, entry); }
          });
        }, { variant: "danger" })
      ]
    });
  }

  function openEditor(dayOfWeek, existing) {
    var count = SM.entriesFor(dayOfWeek).length;
    var state = {
      name: existing ? existing.name : "",
      label: existing ? existing.label : "Training " + (count + 1),
      category: existing ? existing.category : "OTHER",
      unit: existing ? existing.unit : "REPS",
      sets: existing ? existing.sets : 3,
      reps: existing ? existing.reps : 8,
      notes: existing && existing.notes ? existing.notes : "",
      formTip: existing && existing.formTip ? existing.formTip : "",
      manual: !!existing
    };

    var nameInput = h("input", { class: "input", type: "text", placeholder: "Exercise name", value: state.name });
    var labelInput = h("input", { class: "input", type: "text", placeholder: "Label", value: state.label });
    var notesInput = h("textarea", { class: "textarea", placeholder: "Notes (optional)" });
    notesInput.value = state.notes;
    var tipInput = h("textarea", { class: "textarea", placeholder: "Form tip (optional)" });
    tipInput.value = state.formTip;
    var setsInput = h("input", { class: "input", type: "number", min: "1", max: "10", value: String(state.sets) });
    var repsInput = h("input", { class: "input", type: "number", min: "1", max: "600", value: String(state.reps) });
    var repsLabel = h("span", { class: "field__label", text: state.unit === "SECONDS" ? "Seconds" : "Reps" });
    var suggestionsBox = h("div", { class: "chip-row" });
    var categoryRow = h("div", { class: "chip-row" });
    var unitRow = h("div", { class: "chip-row" });
    var selectedLabel = h("span", { class: "label accent", text: "Selected: " + SM.categoryName(state.category) });
    var saveButton = ui.button(existing ? "Save changes" : "Add training", function () { submit(); }, { disabled: !state.name.trim() });

    function renderCategories() {
      categoryRow.innerHTML = "";
      SM.categories.forEach(function (category) {
        categoryRow.appendChild(ui.chip(SM.categoryName(category), state.category === category, function () {
          state.category = category;
          state.manual = true;
          renderCategories();
          selectedLabel.textContent = "Selected: " + SM.categoryName(category);
        }));
      });
    }

    function renderUnits() {
      unitRow.innerHTML = "";
      ["REPS", "SECONDS"].forEach(function (unit) {
        unitRow.appendChild(ui.chip(unit === "REPS" ? "Reps" : "Seconds", state.unit === unit, function () {
          state.unit = unit;
          state.reps = unit === "SECONDS" ? Math.max(state.reps, 5) : Math.min(state.reps, 100);
          repsInput.value = String(state.reps);
          repsLabel.textContent = unit === "SECONDS" ? "Seconds" : "Reps";
          renderUnits();
        }));
      });
    }

    function renderSuggestions() {
      suggestionsBox.innerHTML = "";
      var query = state.name.trim().toLowerCase();
      if (!query) return;
      SM.state.exercises
        .filter(function (exercise) { return exercise.name.toLowerCase().indexOf(query) >= 0 && exercise.name.toLowerCase() !== query; })
        .slice(0, 4)
        .forEach(function (exercise) {
          suggestionsBox.appendChild(ui.chip(exercise.name, false, function () {
            state.name = exercise.name;
            nameInput.value = exercise.name;
            if (!state.manual) {
              var suggestion = SM.suggestCategory(exercise.name);
              if (suggestion) {
                state.category = suggestion;
                renderCategories();
                selectedLabel.textContent = "Selected: " + SM.categoryName(state.category);
              }
            }
            renderSuggestions();
            saveButton.disabled = !state.name.trim();
          }));
        });
    }

    nameInput.addEventListener("input", function () {
      state.name = nameInput.value;
      saveButton.disabled = !state.name.trim();
      if (!state.manual) {
        var suggestion = SM.suggestCategory(state.name);
        if (suggestion) {
          state.category = suggestion;
          renderCategories();
          selectedLabel.textContent = "Selected: " + SM.categoryName(suggestion);
        }
      }
      renderSuggestions();
    });

    setsInput.addEventListener("input", function () {
      state.sets = Math.max(1, Math.min(10, Number(setsInput.value) || 1));
    });
    repsInput.addEventListener("input", function () {
      state.reps = Math.max(1, Math.min(state.unit === "SECONDS" ? 600 : 100, Number(repsInput.value) || 1));
    });
    notesInput.addEventListener("input", function () { state.notes = notesInput.value; });
    tipInput.addEventListener("input", function () { state.formTip = tipInput.value; });

    function submit() {
      var name = state.name.trim();
      if (!name) return;
      handle.close();
      saveEntry(dayOfWeek, existing, {
        name: name,
        label: (labelInput.value || "").trim() || "Training " + (existing ? count : count + 1),
        category: state.category,
        unit: state.unit,
        sets: state.sets,
        reps: state.reps,
        notes: notesInput.value.trim() || null,
        formTip: tipInput.value.trim() || null
      });
    }

    renderCategories();
    renderUnits();
    renderSuggestions();

    var handle = ui.sheet({
      title: existing ? "Edit training" : "Add training",
      body: [
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Exercise name" }), nameInput]),
        suggestionsBox,
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Label" }), labelInput]),
        h("span", { class: "field__label", text: "Category" }),
        categoryRow,
        selectedLabel,
        unitRow,
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Sets" }), setsInput]),
        h("label", { class: "field" }, [repsLabel, repsInput]),
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Notes (optional)" }), notesInput]),
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Form tip (optional)" }), tipInput]),
        saveButton
      ]
    });
  }

  function openRename(dayOfWeek) {
    var day = SM.dayRow(dayOfWeek);
    var input = h("input", { class: "input", type: "text", value: day.name || "", placeholder: SM.weekFull[dayOfWeek - 1] });
    var handle = ui.sheet({
      title: "Rename day",
      body: [
        h("label", { class: "field" }, [h("span", { class: "field__label", text: "Day name" }), input]),
        ui.button("Save", function () {
          day.name = input.value.trim() || null;
          handle.close();
          SM.commit(true);
        }),
        ui.button("Cancel", function () { handle.close(); }, { variant: "ghost" })
      ]
    });
  }

  function render(container, dayOfWeek) {
    var day = SM.dayRow(dayOfWeek);
    var entries = SM.entriesFor(dayOfWeek);
    var stateName = day.state === "TRAINING" ? "Training" : day.state === "OPTIONAL" ? "Optional" : "Rest";

    var page = h("div", { class: "screen" }, [
      ui.screenHead({
        title: day.name || SM.weekFull[dayOfWeek - 1],
        subtitle: stateName + " day",
        back: function () { SM.back(); },
        actions: ui.textButton("Rename", function () { openRename(dayOfWeek); })
      }),
      h("span", { class: "small accent", text: SM.relativeLabel(SM.nextOccurrence(dayOfWeek)) })
    ]);

    if (entries.length === 0) {
      page.appendChild(ui.card([ui.emptyState("No exercises yet")]));
    }

    entries.forEach(function (entry, index) {
      page.appendChild(h("div", { class: "card" }, [
        h("div", { class: "row" }, [
          h("div", { class: "stack--xs grow" }, [
            h("span", { class: "label dim", text: entry.label }),
            h("span", { class: "body-lg", text: entry.name }),
            h("span", { class: "label dim", text: entry.sets + " sets · " + SM.categoryName(entry.category) })
          ]),
          ui.badge(SM.badgeText(entry.sets, entry.reps, entry.unit)),
          ui.iconButton("more", function () { openActions(dayOfWeek, entry, index, entries.length); }, "More")
        ])
      ]));
    });

    page.appendChild(ui.button("+ Add training", function () { openEditor(dayOfWeek, null); }));

    container.appendChild(page);
  }

  SM.route("day-editor", function (container, params) {
    var dayOfWeek = Math.max(1, Math.min(7, Number(params.dayOfWeek) || 1));
    render(container, dayOfWeek);
  });
})();
