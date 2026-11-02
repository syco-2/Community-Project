(function () {
  var photoCache = {};
  var unlockedKey = null;
  var searchQuery = "";
  var ACCENTS = ["#2F80ED", "#FF6B2C", "#22C55E", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4", "#EF4444"];
  var PREFIXES = ["monster energy ", "monster ", "energy "];

  function monsters() {
    if (!Array.isArray(SM.state.monsters)) SM.state.monsters = [];
    return SM.state.monsters;
  }

  function normalize(raw) {
    return String(raw || "").toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, " ")
      .trim()
      .replace(/ +/g, " ");
  }

  function flavorCore(name) {
    var core = normalize(name);
    PREFIXES.forEach(function (prefix) {
      if (core.indexOf(prefix) === 0) core = core.slice(prefix.length).trim();
    });
    return core;
  }

  function score(needle, flavor) {
    var core = flavorCore(flavor.name);
    var value = 0;

    if (core === needle) value = 140;

    if (value === 0) {
      var shorter = Math.min(core.length, needle.length);
      if (shorter >= 3 && (core.indexOf(needle) === 0 || needle.indexOf(core) === 0 || core.indexOf(needle) >= 0 || needle.indexOf(core) >= 0)) {
        value = 120 - Math.max(0, core.length - needle.length);
      }
    }

    if (value === 0) {
      var coreTokens = core.split(" ").filter(Boolean);
      var typedTokens = needle.split(" ").filter(Boolean);
      if (coreTokens.length && typedTokens.length) {
        var overlap = typedTokens.filter(function (token) { return coreTokens.indexOf(token) >= 0; }).length;
        var ratio = overlap / typedTokens.length;
        if (ratio >= 0.5) value = Math.floor(110 * ratio);
      }
    }

    (flavor.keywords || []).forEach(function (keyword) {
      var alias = normalize(keyword);
      if (alias.length < 3) return;
      if (alias === needle) value = Math.max(value, 125);
      else if (alias.length >= 5 && needle.indexOf(alias) >= 0) value = Math.max(value, 100);
      else if (alias.length >= 5 && needle.length >= 4 && alias.indexOf(needle) >= 0) value = Math.max(value, 80);
    });

    return value;
  }

  function catalog() {
    return window.CANS.concat(SM.state.customCans || []);
  }

  function suggestionsFor(typed, limit) {
    var needle = normalize(typed);
    if (needle.length < 2) return [];
    return catalog()
      .map(function (flavor) { return { flavor: flavor, value: score(needle, flavor) }; })
      .filter(function (row) { return row.value > 0; })
      .sort(function (a, b) {
        if (b.value !== a.value) return b.value - a.value;
        var length = flavorCore(a.flavor.name).length - flavorCore(b.flavor.name).length;
        if (length !== 0) return length;
        return a.flavor.slot - b.flavor.slot;
      })
      .slice(0, limit || 4)
      .map(function (row) { return row.flavor; });
  }

  function exactFlavor(name) {
    var typed = String(name || "").trim().toLowerCase();
    return catalog().filter(function (flavor) { return flavor.name.toLowerCase() === typed; })[0] || null;
  }

  function accentFor(name) {
    var hash = 0;
    var lower = String(name || "").toLowerCase();
    for (var index = 0; index < lower.length; index += 1) {
      hash = (hash * 31 + lower.charCodeAt(index)) | 0;
    }
    return ACCENTS[Math.abs(hash) % ACCENTS.length];
  }

  function latestCapture(speciesId) {
    return monsters()
      .filter(function (monster) { return monster.speciesId === speciesId; })
      .sort(function (a, b) { return b.collectedAt - a.collectedAt; })[0] || null;
  }

  function cabinet() {
    var shipped = catalog().slice()
      .sort(function (a, b) { return a.slot - b.slot; })
      .map(function (flavor) {
        return {
          key: flavor.id,
          name: flavor.name,
          color: flavor.color,
          theme: flavor.theme || "",
          capture: latestCapture(flavor.id)
        };
      });

    var known = {};
    catalog().forEach(function (flavor) { known[flavor.id] = true; });

    var custom = monsters()
      .filter(function (monster) { return !monster.speciesId || !known[monster.speciesId]; })
      .sort(function (a, b) { return a.collectedAt - b.collectedAt; })
      .map(function (monster) {
        return {
          key: monster.id,
          name: monster.name,
          color: monster.color,
          theme: "",
          capture: monster
        };
      });

    return shipped.concat(custom);
  }

  function hexAlpha(hex, alpha) {
    var value = String(hex || "#2F80ED").replace("#", "");
    if (value.length === 3) value = value[0] + value[0] + value[1] + value[1] + value[2] + value[2];
    var red = parseInt(value.slice(0, 2), 16) || 47;
    var green = parseInt(value.slice(2, 4), 16) || 128;
    var blue = parseInt(value.slice(4, 6), 16) || 237;
    return "rgba(" + red + ", " + green + ", " + blue + ", " + alpha + ")";
  }

  function squareJpeg(file, size) {
    return SM.readFile(file, true).then(function (dataUrl) {
      return new Promise(function (resolve) {
        var image = new Image();
        image.onload = function () {
          var side = Math.min(image.width, image.height);
          var canvas = document.createElement("canvas");
          canvas.width = size;
          canvas.height = size;
          var context = canvas.getContext("2d");
          context.drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, size, size);
          resolve(canvas.toDataURL("image/jpeg", 0.9));
        };
        image.onerror = function () { resolve(dataUrl); };
        image.src = dataUrl;
      });
    });
  }

  function addCan(name, dataUrl) {
    var flavor = exactFlavor(name);
    var speciesId = flavor ? flavor.id : "";
    var previous = flavor ? latestCapture(flavor.id) : null;
    var id = SM.uid();
    var key = "monster:" + id;

    return SM.filePut(key, dataUrl).then(function () {
      if (previous) {
        SM.state.monsters = monsters().filter(function (monster) { return monster.id !== previous.id; });
        SM.fileDelete(previous.photoKey);
        delete photoCache[previous.photoKey];
      }
      monsters().push({
        id: id,
        speciesId: speciesId,
        name: name.trim(),
        color: flavor ? flavor.color : accentFor(name),
        slot: flavor ? flavor.slot : 1000 + monsters().length,
        photoKey: key,
        collectedAt: Date.now()
      });
      SM.commit(false);
      unlockedKey = flavor ? flavor.id : id;
      return { name: name.trim(), key: unlockedKey };
    });
  }

  function removeCan(entry) {
    var monster = entry.capture;
    if (!monster) return;
    SM.state.monsters = monsters().filter(function (row) { return row.id !== monster.id; });
    SM.fileDelete(monster.photoKey);
    delete photoCache[monster.photoKey];
    if (unlockedKey === entry.key) unlockedKey = null;
    SM.commit(true);
  }

  function mountPhoto(node, photoKey) {
    if (!photoKey) return;
    if (photoCache[photoKey]) {
      node.src = photoCache[photoKey];
      node.hidden = false;
      return;
    }
    SM.fileGet(photoKey).then(function (value) {
      if (!value) return;
      photoCache[photoKey] = value;
      node.src = value;
      node.hidden = false;
    });
  }

  function silhouette() {
    var wrap = SM.ui.h("span", { class: "can-silhouette" });
    wrap.innerHTML =
      '<svg viewBox="0 0 100 140" fill="none" aria-hidden="true">' +
      '<rect x="22" y="12" width="56" height="116" rx="12" fill="#1B2430"/>' +
      '<rect x="22" y="12" width="56" height="6" rx="3" fill="#243040"/>' +
      '<rect x="22" y="122" width="56" height="6" rx="3" fill="#243040" opacity="0.5"/>' +
      '<path d="M48 34 L43 86 L47 86 L51 34 Z" fill="#FFFFFF" opacity="0.05"/>' +
      '<path d="M57 34 L52 86 L56 86 L60 34 Z" fill="#FFFFFF" opacity="0.05"/>' +
      '<path d="M66 34 L61 86 L65 86 L69 34 Z" fill="#FFFFFF" opacity="0.05"/>' +
      "</svg>";
    return wrap;
  }

  function slotNode(entry, handleLongPress) {
    var unlocked = Boolean(entry.capture);
    var frame = SM.ui.h("span", {
      class: "can-slot__frame",
      style: unlocked
        ? { background: hexAlpha(entry.color, 0.12) }
        : { background: "rgba(28, 38, 51, 0.28)" }
    });
    if (unlocked) {
      var photo = SM.ui.h("img", { class: "can-slot__photo", alt: entry.name, hidden: true });
      frame.appendChild(silhouette());
      frame.appendChild(photo);
      mountPhoto(photo, entry.capture.photoKey);
      photo.addEventListener("error", function () { photo.remove(); });
    } else {
      frame.appendChild(silhouette());
    }

    var node = SM.ui.h("button", {
      class: "can-slot" + (unlocked ? " is-unlocked" : "") + (unlockedKey === entry.key ? " is-dropped" : ""),
      type: "button",
      "data-key": entry.key,
      "data-name": entry.name.toLowerCase(),
      "aria-label": entry.name
    }, [
      frame,
      unlocked ? SM.ui.h("span", { class: "can-slot__name", text: entry.name }) : null
    ]);

    if (unlocked) handleLongPress(node, entry);
    return node;
  }

  function longPress(node, onLong) {
    var timer = null;

    function stop() {
      if (timer) {
        window.clearTimeout(timer);
        timer = null;
      }
    }

    node.addEventListener("pointerdown", function () {
      stop();
      timer = window.setTimeout(function () {
        timer = null;
        onLong();
      }, 600);
    });
    ["pointerup", "pointerleave", "pointercancel"].forEach(function (event) {
      node.addEventListener(event, stop);
    });
    node.addEventListener("contextmenu", function (event) {
      event.preventDefault();
      onLong();
    });
  }

  function openEntry(entry) {
    var photo = SM.ui.h("div", { class: "can-photo-lg" + (entry.capture ? " is-filled" : "") }, [silhouette()]);
    if (entry.capture) {
      var image = SM.ui.h("img", { alt: entry.name, hidden: true });
      photo.appendChild(image);
      mountPhoto(image, entry.capture.photoKey);
    }

    var meta = SM.ui.h("div", { class: "stack--xs grow" }, [
      SM.ui.h("p", {
        class: "small dim",
        text: entry.capture && entry.capture.collectedAt
          ? "Added " + SM.formatShort(new Date(entry.capture.collectedAt))
          : "Your own photo"
      })
    ]);

    var handle = SM.ui.sheet({
      title: entry.name,
      body: [
        SM.ui.h("div", { class: "can-entry-row" }, [photo, meta]),
        SM.ui.button("Delete", function () {
          handle.close();
          removeCan(entry);
          SM.ui.toast("Can removed");
        }, { variant: "danger" }),
        SM.ui.button("Cancel", function () { handle.close(); }, { variant: "ghost" })
      ]
    });
  }

  function nameDialog(dataUrl, onSaved) {
    var preview = SM.ui.h("div", { class: "can-preview is-filled" }, [
      SM.ui.h("img", { src: dataUrl, alt: "Your can photo" })
    ]);
    var input = SM.ui.h("input", {
      class: "input",
      type: "text",
      maxlength: "48",
      placeholder: "Ultra Watermelon",
      autocomplete: "off"
    });
    var chips = SM.ui.h("div", { class: "chip-row can-suggestions" });
    var confirm = SM.ui.button("Add", function () { submit(); }, { disabled: true });
    var saving = false;

    function renderSuggestions() {
      chips.innerHTML = "";
      suggestionsFor(input.value, 4).forEach(function (flavor) {
        chips.appendChild(SM.ui.chip(flavor.name, false, function () {
          input.value = flavor.name;
          renderSuggestions();
          updateState();
        }));
      });
    }

    function updateState() {
      confirm.disabled = input.value.trim().length === 0 || saving;
    }

    function submit() {
      var name = input.value.trim();
      if (!name || saving) return;
      saving = true;
      confirm.disabled = true;
      confirm.textContent = "Adding…";
      addCan(name, dataUrl).then(function (result) {
        handle.close();
        SM.ui.toast(result.name);
        onSaved();
      }).catch(function () {
        saving = false;
        confirm.textContent = "Add";
        updateState();
        SM.ui.toast("Could not add that can.");
      });
    }

    input.addEventListener("input", function () {
      renderSuggestions();
      updateState();
    });

    var panel = SM.ui.h("div", { class: "dialog can-name-dialog" }, [
      SM.ui.h("h2", { class: "title-lg", text: "Name this can" }),
      SM.ui.h("div", { class: "stack", style: { marginTop: "12px" } }, [
        preview,
        SM.ui.h("label", { class: "field" }, [
          SM.ui.h("span", { class: "field__label", text: "Flavour" }),
          input
        ]),
        chips,
        confirm,
        SM.ui.button("Cancel", function () { handle.close(); }, { variant: "ghost" })
      ])
    ]);

    var handle = SM.ui.overlay(panel, true);
  }

  function openAddFlow() {
    var picker = SM.ui.h("input", { type: "file", accept: "image/*", class: "hidden" });
    picker.setAttribute("capture", "environment");
    document.body.appendChild(picker);

    picker.addEventListener("change", function () {
      var file = picker.files && picker.files[0];
      picker.remove();
      if (!file) return;
      var scanner = SM.lottie.overlay("scanner", { duration: 2400 });
      squareJpeg(file, 720).then(function (dataUrl) {
        scanner.close();
        nameDialog(dataUrl, function () {
          SM.refresh();
        });
      }).catch(function () {
        scanner.close();
        SM.ui.toast("Could not read that photo.");
      });
    });

    picker.click();
  }

  function render(container) {
    var entries = cabinet();
    var collected = entries.filter(function (entry) { return entry.capture; }).length;
    var sections = [];
    var grid = SM.ui.h("div", { class: "can-grid" });

    var grouped = [];
    entries.forEach(function (entry) {
      var theme = entry.theme || "";
      var group = grouped.filter(function (row) { return row.theme === theme; })[0];
      if (!group) {
        group = { theme: theme, entries: [] };
        grouped.push(group);
      }
      group.entries.push(entry);
    });

    grouped.forEach(function (group) {
      var header = null;
      if (group.theme) {
        header = SM.ui.h("div", { class: "can-section" }, [
          SM.ui.h("span", { class: "title-md", text: group.theme }),
          SM.ui.h("span", { class: "can-section__line" })
        ]);
        grid.appendChild(header);
      }
      var slots = [];
      group.entries.forEach(function (entry) {
        var node = slotNode(entry, longPress);
        if (entry.capture) {
          node.addEventListener("click", function () { openEntry(entry); });
        }
        grid.appendChild(node);
        slots.push(node);
      });
      sections.push({ header: header, slots: slots });
    });

    var search = SM.ui.h("input", {
      class: "input can-search",
      type: "search",
      placeholder: "Search",
      value: searchQuery
    });
    search.addEventListener("input", function () {
      searchQuery = search.value;
      applyFilter();
    });

    function applyFilter() {
      var needle = searchQuery.trim().toLowerCase();
      sections.forEach(function (section) {
        var visible = 0;
        section.slots.forEach(function (slot) {
          var show = !needle || slot.getAttribute("data-name").indexOf(needle) >= 0;
          slot.hidden = !show;
          if (show) visible += 1;
        });
        if (section.header) section.header.hidden = visible === 0;
      });
    }

    var page = SM.ui.h("div", { class: "screen monsters-page" }, [
      SM.ui.screenHead({
        title: "Monstr",
        actions: SM.ui.h("span", { class: "monsters-count", text: collected + " / " + entries.length })
      }),
      search,
      SM.ui.button("Add can", openAddFlow, { icon: "camera" }),
      grid
    ]);

    container.appendChild(page);
    applyFilter();

    if (unlockedKey) {
      var key = unlockedKey;
      window.setTimeout(function () {
        if (unlockedKey === key) unlockedKey = null;
      }, 760);
    }
  }

  SM.route("monsters", render);
})();
