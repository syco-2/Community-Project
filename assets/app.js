(function () {
  var library = document.getElementById("library");
  var screen = document.getElementById("loader-screen");
  var app = document.getElementById("app");
  var toggle = document.getElementById("theme-toggle");
  var entries = Array.isArray(window.APPS) ? window.APPS : [];

  function saveTheme(value) {
    try {
      window.localStorage.setItem("theme", value);
    } catch (error) {
      return;
    }
  }

  function readTheme() {
    try {
      return window.localStorage.getItem("theme");
    } catch (error) {
      return null;
    }
  }

  function syncTheme() {
    var dark = document.documentElement.getAttribute("data-theme") === "dark";
    toggle.textContent = dark ? "Light" : "Dark";
  }

  if (readTheme() === "dark") {
    document.documentElement.setAttribute("data-theme", "dark");
  }
  syncTheme();

  toggle.addEventListener("click", function () {
    if (document.documentElement.getAttribute("data-theme") === "dark") {
      document.documentElement.removeAttribute("data-theme");
      saveTheme("light");
    } else {
      document.documentElement.setAttribute("data-theme", "dark");
      saveTheme("dark");
    }
    syncTheme();
  });

  function action(label, url, download) {
    var node;
    if (url) {
      node = document.createElement("a");
      node.href = url;
      node.rel = "noopener noreferrer";
      if (download) {
        node.setAttribute("download", "");
      } else {
        node.target = "_blank";
      }
    } else {
      node = document.createElement("button");
      node.type = "button";
      node.disabled = true;
    }
    node.className = "btn";
    node.textContent = label;
    return node;
  }

  function card(entry) {
    var item = document.createElement("article");
    item.className = "card";

    var thumb = document.createElement("div");
    thumb.className = "thumb";
    if (entry.thumb) {
      var image = document.createElement("img");
      image.src = entry.thumb;
      image.alt = "";
      image.width = 640;
      image.height = 400;
      thumb.appendChild(image);
    }

    var body = document.createElement("div");
    body.className = "card-body";

    var name = document.createElement("h2");
    name.className = "app-name";
    name.textContent = entry.name || "";

    var description = document.createElement("p");
    description.className = "app-desc";
    if (entry.description) {
      description.textContent = entry.description;
    } else if (entry.logo) {
      description.classList.add("is-logo");
      var logo = document.createElement("img");
      logo.className = "app-logo";
      logo.src = entry.logo;
      logo.alt = "";
      logo.width = 96;
      logo.height = 96;
      description.appendChild(logo);
    }

    var inspired = document.createElement("p");
    inspired.className = "app-inspired";
    inspired.textContent = entry.inspiredBy ? "Inspired by @" + entry.inspiredBy : "Inspired by";

    var actions = document.createElement("div");
    actions.className = "actions";
    actions.appendChild(action("Web-App öffnen", entry.webUrl, false));
    if (entry.apk !== false) {
      actions.appendChild(action("APK herunterladen", entry.apkUrl, true));
    }

    var text = document.createElement("div");
    text.className = "card-text";
    text.appendChild(name);
    text.appendChild(description);

    body.appendChild(text);
    body.appendChild(inspired);
    body.appendChild(actions);

    item.appendChild(thumb);
    item.appendChild(body);
    return item;
  }

  entries.forEach(function (entry) {
    library.appendChild(card(entry));
  });

  function reveal() {
    screen.classList.add("is-done");
    app.classList.add("is-ready");
    window.setTimeout(function () {
      if (screen.parentNode) {
        screen.parentNode.removeChild(screen);
      }
    }, 560);
  }

  function start() {
    window.setTimeout(reveal, 320);
  }

  if (document.readyState === "complete") {
    start();
  } else {
    window.addEventListener("load", start);
  }
})();
