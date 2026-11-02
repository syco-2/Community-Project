(function () {
  var audio = new Audio();
  audio.preload = "metadata";
  var currentUrl = null;
  var currentIndex = -1;
  var shuffleOn = false;
  var activeRoot = null;
  var activePaint = null;

  function tracks() {
    return SM.state.music || [];
  }

  function icon(name, size) {
    var paths = {
      play: '<path d="M7 4l13 8-13 8z"/>',
      pause: '<rect x="7" y="4" width="4" height="16" rx="1"/><rect x="15" y="4" width="4" height="16" rx="1"/>',
      previous: '<path d="M18 5l-9 7 9 7z"/><path d="M6 5v14"/>',
      next: '<path d="M6 5l9 7-9 7z"/><path d="M18 5v14"/>',
      shuffle: '<path d="M3 7h4l10 10h4"/><path d="M3 17h4l3-3"/><path d="M18 4l3 3-3 3"/><path d="M18 14l3 3-3 3"/>'
    };
    var node = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    node.setAttribute("viewBox", "0 0 24 24");
    node.setAttribute("fill", "none");
    node.setAttribute("stroke", "currentColor");
    node.setAttribute("stroke-width", "1.8");
    node.setAttribute("stroke-linecap", "round");
    node.setAttribute("stroke-linejoin", "round");
    node.setAttribute("width", size || 22);
    node.setAttribute("height", size || 22);
    node.innerHTML = paths[name] || paths.play;
    return node;
  }

  function currentTrack() {
    return tracks()[currentIndex] || null;
  }

  function paint() {
    if (activeRoot && activeRoot.isConnected && typeof activePaint === "function") {
      activePaint();
    }
  }

  function playIndex(index, autoplay) {
    var list = tracks();
    if (!list.length) return;
    var track = list[index];
    if (!track) return;
    SM.fileGet("music:" + track.id).then(function (blob) {
      if (!blob) {
        SM.ui.toast("Track not available");
        return;
      }
      if (currentUrl) URL.revokeObjectURL(currentUrl);
      currentUrl = URL.createObjectURL(blob);
      audio.src = currentUrl;
      currentIndex = index;
      if (autoplay !== false) {
        audio.play().catch(function () {
          SM.ui.toast("Playback blocked");
        });
      }
      paint();
      SM.refresh();
    });
  }

  function togglePlay() {
    if (!tracks().length) return;
    if (!audio.src) {
      playIndex(0, true);
      return;
    }
    if (audio.paused) {
      audio.play().catch(function () {
        SM.ui.toast("Playback blocked");
      });
    } else {
      audio.pause();
    }
  }

  function nextIndex(step) {
    var list = tracks();
    if (!list.length) return -1;
    if (shuffleOn && list.length > 1) {
      var candidate = currentIndex;
      while (candidate === currentIndex) {
        candidate = Math.floor(Math.random() * list.length);
      }
      return candidate;
    }
    if (currentIndex < 0) return 0;
    return (currentIndex + step + list.length) % list.length;
  }

  function skip(step) {
    var target = nextIndex(step);
    if (target >= 0) playIndex(target, true);
  }

  function setShuffle(enabled) {
    shuffleOn = enabled;
    if (enabled && tracks().length && !audio.src) {
      playIndex(Math.floor(Math.random() * tracks().length), false);
    }
    paint();
  }

  function removeTrack(track) {
    var list = tracks();
    var index = list.indexOf(track);
    if (index < 0) return;
    SM.fileDelete("music:" + track.id);
    if (index === currentIndex) {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
      if (currentUrl) {
        URL.revokeObjectURL(currentUrl);
        currentUrl = null;
      }
      currentIndex = -1;
    } else if (index < currentIndex) {
      currentIndex -= 1;
    }
    list.splice(index, 1);
    SM.commit();
    SM.refresh();
  }

  function moveTrack(from, to, track) {
    var list = tracks();
    if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return;
    var playingId = currentTrack() ? currentTrack().id : null;
    list.splice(from, 1);
    list.splice(to, 0, track);
    if (playingId) {
      currentIndex = list.findIndex(function (item) { return item.id === playingId; });
    }
    SM.commit();
    SM.refresh();
  }

  function pickFiles() {
    Array.prototype.forEach.call(document.querySelectorAll(".music-file-input"), function (node) {
      node.remove();
    });
    var input = SM.ui.h("input", { class: "music-file-input", type: "file", accept: "audio/*", multiple: true, style: { display: "none" } });
    input.addEventListener("change", function () {
      var files = Array.prototype.slice.call(input.files || []);
      if (!files.length) return;
      Promise.all(files.map(function (file) {
        var id = SM.uid();
        return SM.filePut("music:" + id, file).then(function () {
          return { id: id, name: file.name, size: file.size };
        });
      })).then(function (added) {
        SM.state.music = tracks().concat(added);
        SM.commit();
        input.remove();
        SM.refresh();
      });
    });
    document.body.appendChild(input);
    input.click();
  }

  function moveButton(label, enabled, onClick) {
    var node = SM.ui.textButton(label, onClick, "music-track__move");
    node.disabled = !enabled;
    return node;
  }

  function transportButton(name, label, options) {
    var opts = options || {};
    var node = SM.ui.h("button", {
      class: "music-transport__btn" + (opts.primary ? " is-primary" : "") + (opts.toggled ? " is-on" : ""),
      type: "button",
      "aria-label": label,
      disabled: opts.disabled === true,
      on: { click: opts.onClick }
    }, [icon(name, opts.primary ? 32 : 22)]);
    return node;
  }

  SM.route("music", function (container) {
    var root = SM.ui.h("div", { class: "screen music-page" });
    var list = tracks();
    var playing = !audio.paused && !!audio.src;

    var playButton = transportButton(playing ? "pause" : "play", playing ? "Pause" : "Play", {
      primary: true,
      disabled: list.length === 0,
      onClick: togglePlay
    });

    var shuffleButton = transportButton("shuffle", shuffleOn ? "Shuffle on" : "Shuffle off", {
      disabled: list.length === 0,
      toggled: shuffleOn,
      onClick: function () { setShuffle(!shuffleOn); }
    });

    var timeLabel = SM.ui.h("span", { class: "small dim music-time", text: "0:00" });
    var durationLabel = SM.ui.h("span", { class: "small dim music-time", text: "0:00" });
    var seek = SM.ui.h("input", { class: "music-seek", type: "range", min: "0", max: "1000", value: "0" });
    seek.addEventListener("input", function () {
      if (!audio.duration) return;
      audio.currentTime = (Number(seek.value) / 1000) * audio.duration;
      paint();
    });

    var nowTitle = SM.ui.h("p", { class: "body music-now__title", text: currentTrack() ? currentTrack().name : "No tracks" });

    var transport = SM.ui.card([
      SM.ui.h("div", { class: "music-transport" }, [
        shuffleButton,
        SM.ui.h("button", {
          class: "music-transport__btn",
          type: "button",
          "aria-label": "Previous",
          disabled: list.length === 0,
          on: { click: function () { skip(-1); } }
        }, [icon("previous")]),
        playButton,
        SM.ui.h("button", {
          class: "music-transport__btn",
          type: "button",
          "aria-label": "Next",
          disabled: list.length === 0,
          on: { click: function () { skip(1); } }
        }, [icon("next")])
      ]),
      SM.ui.h("div", { class: "music-now" }, [nowTitle]),
      SM.ui.h("div", { class: "music-seek-row" }, [timeLabel, seek, durationLabel])
    ]);

    function paintScreen() {
      var isPlaying = !audio.paused && !!audio.src;
      playButton.replaceChildren(icon(isPlaying ? "pause" : "play", 32));
      playButton.setAttribute("aria-label", isPlaying ? "Pause" : "Play");
      shuffleButton.classList.toggle("is-on", shuffleOn);
      var track = currentTrack();
      nowTitle.textContent = track ? track.name : "No tracks";
      var duration = Number.isFinite(audio.duration) ? audio.duration : 0;
      var position = Number.isFinite(audio.currentTime) ? audio.currentTime : 0;
      timeLabel.textContent = SM.formatTime(Math.floor(position));
      durationLabel.textContent = SM.formatTime(Math.floor(duration));
      seek.value = duration > 0 ? String(Math.round((position / duration) * 1000)) : "0";
      Array.prototype.forEach.call(root.querySelectorAll(".music-track"), function (row) {
        var index = Number(row.getAttribute("data-index"));
        row.classList.toggle("is-playing", index === currentIndex && !audio.paused);
      });
    }

    activeRoot = root;
    activePaint = paintScreen;

    root.appendChild(SM.ui.screenHead({
      title: "Music",
      subtitle: list.length ? list.length + (list.length === 1 ? " track" : " tracks") : null,
      actions: SM.ui.textButton("+", pickFiles)
    }));
    root.appendChild(transport);

    if (list.length === 0) {
      root.appendChild(SM.ui.h("p", { class: "body dim", text: "No tracks" }));
    } else {
      var rows = list.map(function (track, index) {
        var row = SM.ui.h("div", {
          class: "music-track",
          "data-index": index
        }, [
          SM.ui.h("span", { class: "music-track__dot" }),
          SM.ui.h("button", {
            class: "music-track__name",
            type: "button",
            on: { click: function () { playIndex(index, true); } }
          }, [track.name]),
          moveButton("↑", index > 0, function () { moveTrack(index, index - 1, track); }),
          moveButton("↓", index < list.length - 1, function () { moveTrack(index, index + 1, track); }),
          SM.ui.textButton("×", function () { removeTrack(track); }, "music-track__remove")
        ]);
        return row;
      });

      root.appendChild(SM.ui.card(rows, "music-list"));
    }

    container.appendChild(root);
    paintScreen();
  });

  audio.addEventListener("play", paint);
  audio.addEventListener("pause", paint);
  audio.addEventListener("timeupdate", function () {
    if (activeRoot && activeRoot.isConnected && activePaint) activePaint();
  });
  audio.addEventListener("loadedmetadata", paint);
  audio.addEventListener("ended", function () {
    var target = nextIndex(1);
    if (target >= 0) playIndex(target, true);
  });

  window.SMMusic = {
    playForWorkout: function () {
      if (!SM.state.settings.musicWithWorkout) return;
      if (!audio.paused) return;
      var list = tracks();
      if (!list.length) return;
      shuffleOn = true;
      playIndex(Math.floor(Math.random() * list.length), true);
    },
    pause: function () {
      audio.pause();
    },
    isReady: function () {
      return !audio.paused;
    }
  };
})();
