(function () {
  var h = SM.ui.h;
  var message = null;
  var listMessage = null;

  function setSetting(key, value) {
    SM.state.settings[key] = value;
    SM.commit(true);
  }

  function toggleRow(label, checked, onChange) {
    return h("div", { class: "toggle-row" }, [
      h("span", { class: "title-md", text: label }),
      h("button", {
        class: "switch" + (checked ? " is-on" : ""),
        type: "button",
        role: "switch",
        "aria-checked": checked ? "true" : "false",
        on: { click: function () { onChange(!checked); } }
      }, [h("span", { class: "switch__knob" })])
    ]);
  }

  function formatDate(millis) {
    var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    var date = new Date(millis);
    return date.getDate() + " " + months[date.getMonth()] + " " + date.getFullYear() + ", " +
      String(date.getHours()).padStart(2, "0") + ":" + String(date.getMinutes()).padStart(2, "0");
  }

  function validBackup(value) {
    return !!value && typeof value === "object" &&
      !!value.settings && typeof value.settings === "object" &&
      Array.isArray(value.days) &&
      Array.isArray(value.entries) &&
      Array.isArray(value.sessions) &&
      Array.isArray(value.setLogs) &&
      !!value.streak && typeof value.streak === "object";
  }

  function replaceState(parsed) {
    Object.keys(SM.state).forEach(function (key) { delete SM.state[key]; });
    Object.keys(parsed).forEach(function (key) { SM.state[key] = parsed[key]; });
    if (!Array.isArray(SM.state.monsters)) SM.state.monsters = [];
    if (!Array.isArray(SM.state.music)) SM.state.music = [];
    if (!Array.isArray(SM.state.customCans)) SM.state.customCans = [];
    SM.ensureWeek();
    SM.evaluateStreak();
    SM.save();
  }

  function exportBackup() {
    var text = JSON.stringify(SM.state, null, 2);
    var blob = new Blob([text], { type: "application/json" });
    var url = URL.createObjectURL(blob);
    var anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "sixmonths-backup.json";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    message = "Backup written (" + blob.size + " bytes).";
    SM.refresh();
  }

  function importBackup() {
    var input = document.createElement("input");
    input.type = "file";
    input.accept = "application/json,.json";
    input.addEventListener("change", function () {
      var file = input.files && input.files[0];
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () {
        var parsed = null;
        try {
          parsed = JSON.parse(reader.result);
        } catch (error) {
          parsed = null;
        }
        if (!validBackup(parsed)) {
          message = "Import failed: invalid backup file.";
          SM.refresh();
          return;
        }
        SM.ui.dialog({
          title: "Replace all data?",
          confirmLabel: "Import",
          onConfirm: function () {
            replaceState(parsed);
            message = "Import complete.";
            SM.refresh();
          }
        });
      };
      reader.readAsText(file);
    });
    input.click();
  }

  function autoBackup() {
    var settings = SM.state.settings;
    if (!settings.autoBackupEnabled) return;
    var last = settings.lastAutoBackupAt || 0;
    if (Date.now() - last < 7 * 86400000) return;
    SM.filePut("backup:auto", JSON.stringify(SM.state)).then(function () {
      settings.lastAutoBackupAt = Date.now();
      SM.save();
      SM.refresh();
    }).catch(function () {});
  }

  function addCustomList(text) {
    var lines = text.split(/\r?\n/)
      .map(function (line) { return line.replace(/^[-*]\s*/, "").trim(); })
      .filter(function (line) { return line.length > 0; });
    if (!lines.length) return 0;
    var palette = ["#7AB800", "#1E90FF", "#FF6B2C", "#C2185B", "#8E24AA", "#00ACC1"];
    var existing = SM.state.customCans || [];
    var baseSlot = (window.CANS || []).length + existing.length;
    var added = lines.map(function (line, index) {
      return {
        id: "custom-" + SM.uid(),
        name: line,
        color: palette[index % palette.length],
        slot: baseSlot + index,
        theme: "Custom"
      };
    });
    SM.state.customCans = existing.concat(added);
    SM.save();
    return added.length;
  }

  function openListDialog() {
    var text = "";
    var area = h("textarea", {
      class: "textarea",
      style: { height: "260px" },
      on: { input: function (event) { text = event.target.value; } }
    });
    var panel = h("div", { class: "dialog" }, [
      h("h2", { class: "title-lg", text: "New list" }),
      h("div", { style: { marginTop: "12px" } }, [area]),
      h("div", { class: "stack", style: { marginTop: "16px" } }, [
        h("button", {
          class: "btn",
          type: "button",
          on: {
            click: function () {
              var count = addCustomList(text);
              if (count > 0) {
                listMessage = "+" + count;
                handle.close();
                SM.refresh();
              }
            }
          }
        }, ["Add"]),
        h("button", {
          class: "btn btn--ghost",
          type: "button",
          on: { click: function () { handle.close(); } }
        }, ["Cancel"])
      ])
    ]);
    var handle = SM.ui.overlay(panel, true);
  }

  SM.route("settings", function (container) {
    var settings = SM.state.settings;
    var lastBackup = settings.lastAutoBackupAt || 0;

    container.appendChild(h("div", { class: "screen settings-page" }, [
      SM.ui.screenHead({ title: "Settings", back: function () { SM.back(); } }),

      SM.ui.card([
        toggleRow("Music during workout", settings.musicWithWorkout !== false, function (value) {
          setSetting("musicWithWorkout", value);
        })
      ]),

      SM.ui.card([
        toggleRow("Animations", settings.animationsEnabled !== false, function (value) {
          setSetting("animationsEnabled", value);
        }),
        toggleRow("Sound effects", settings.sfxEnabled !== false, function (value) {
          setSetting("sfxEnabled", value);
        })
      ]),

      SM.ui.card([
        h("h2", { class: "title-md", text: "Backup" }),
        h("div", { class: "row" }, [
          SM.ui.textButton("Export", exportBackup),
          SM.ui.textButton("Import", importBackup)
        ]),
        message ? h("p", { class: "small muted", text: message }) : null,
        toggleRow("Weekly automatic backup", settings.autoBackupEnabled === true, function (value) {
          setSetting("autoBackupEnabled", value);
        }),
        lastBackup > 0 ? h("p", { class: "small muted", text: "Last backup " + formatDate(lastBackup) }) : null
      ]),

      SM.ui.card([
        SM.ui.textButton("New list", openListDialog),
        listMessage ? h("p", { class: "small muted", text: listMessage }) : null
      ])
    ]));

    window.setTimeout(autoBackup, 0);
  });
})();
