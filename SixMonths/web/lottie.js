(function () {
  var cache = {};

  function fallback(container, name) {
    container.innerHTML = "";
    if (!container) return;
    var icon = document.createElement("div");
    icon.style.width = "100%";
    icon.style.height = "100%";
    icon.style.color = name === "fire" ? "#FF8A3D" : "#FFD166";
    icon.appendChild(SM.ui.svg(name === "scanner" ? "camera" : name === "trophy" ? "trophy" : "flame"));
    container.appendChild(icon);
  }

  function start(container, data, options, name) {
    if (!window.lottie) {
      fallback(container, name);
      return null;
    }
    container.innerHTML = "";
    var animation = window.lottie.loadAnimation({
      container: container,
      renderer: "svg",
      loop: options.loop !== false,
      autoplay: options.autoplay !== false,
      animationData: data
    });
    if (options.speed) animation.setSpeed(options.speed);
    return animation;
  }

  function mount(container, name, options) {
    var opts = options || {};
    if (!container) return null;
    container.innerHTML = "";
    if (cache[name]) return start(container, cache[name], opts, name);
    fetch("web/data/" + name + ".json")
      .then(function (response) { return response.json(); })
      .then(function (data) {
        cache[name] = data;
        start(container, data, opts, name);
      })
      .catch(function () {
        fallback(container, name);
      });
    return null;
  }

  function overlay(name, options) {
    var opts = options || {};
    var art = SM.ui.h("div", { class: "lottie-layer__art" });
    var layer = SM.ui.h("div", { class: "lottie-layer" }, [art]);
    var root = document.getElementById("overlay-root");
    if (!root) return { close: function () {} };
    root.appendChild(layer);
    mount(art, name, { loop: true });
    var closed = false;
    function close() {
      if (closed) return;
      closed = true;
      layer.remove();
    }
    if (opts.duration) window.setTimeout(close, opts.duration);
    return { close: close, node: layer };
  }

  SM.lottie = {
    mount: mount,
    overlay: overlay,
    open: function () { return cache; }
  };
})();
