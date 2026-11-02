(function () {
  var view = document.getElementById("view");
  var tabBar = document.getElementById("tab-bar");
  var splash = document.getElementById("splash");
  var tabs = [
    { route: "dashboard", label: "Home", icon: "home" },
    { route: "train", label: "Train", icon: "train" },
    { route: "monsters", label: "Monstr", icon: "can" },
    { route: "music", label: "Music", icon: "music" },
    { route: "profile", label: "Profile", icon: "person" }
  ];
  var current = "";

  function buildTabs() {
    tabs.forEach(function (tab) {
      var node = SM.ui.h("button", {
        class: "tab",
        type: "button",
        "data-route": tab.route,
        on: {
          click: function () {
            if (current !== tab.route) SM.go(tab.route);
          }
        }
      }, [SM.ui.svg(tab.icon), SM.ui.h("span", { text: tab.label })]);
      tabBar.appendChild(node);
    });
  }

  function updateTabs(route) {
    current = route;
    var isTab = tabs.some(function (tab) { return tab.route === route; });
    tabBar.hidden = !isTab;
    Array.prototype.forEach.call(tabBar.children, function (node) {
      node.classList.toggle("is-on", node.getAttribute("data-route") === route);
    });
  }

  function render() {
    var parsed = SM.parseRoute(window.location.hash);
    var route = parsed.name;
    if (!route) {
      SM.go(SM.state.settings.onboardingCompleted ? "dashboard" : "onboarding/welcome");
      return;
    }
    var handler = SM.routes[route];
    if (SM.cleanup) {
      SM.cleanup();
      SM.cleanup = null;
    }
    view.innerHTML = "";
    if (!handler) {
      view.appendChild(SM.ui.h("div", { class: "screen" }, [
        SM.ui.card([
          SM.ui.h("h1", { class: "title-xl", text: "Not found" }),
          SM.ui.button("Back", function () { SM.go("dashboard"); }, { variant: "ghost" })
        ])
      ]));
      updateTabs(route);
      return;
    }
    handler(view, parsed.params);
    updateTabs(route);
    window.scrollTo(0, 0);
  }

  SM.refresh = render;
  SM.ensureWeek();
  SM.evaluateStreak();
  buildTabs();
  window.addEventListener("hashchange", render);
  render();

  function hideSplash() {
    if (!splash) return;
    splash.classList.add("is-done");
    window.setTimeout(function () {
      if (splash.parentNode) splash.parentNode.removeChild(splash);
    }, 360);
  }

  if (document.readyState === "complete") {
    window.setTimeout(hideSplash, 260);
  } else {
    window.addEventListener("load", function () { window.setTimeout(hideSplash, 260); });
  }
})();
