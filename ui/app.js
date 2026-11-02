(() => {
  const links = Array.from(document.querySelectorAll(".main-nav a[data-nav-target]"));
  if (!links.length) return;

  const items = links
    .map((link) => ({
      link,
      section: document.getElementById(link.dataset.navTarget),
    }))
    .filter((item) => item.section);

  if (!items.length) return;

  const header = document.querySelector(".site-header");
  let frame = 0;
  let lockUntil = 0;

  const setActive = (activeLink) => {
    for (const { link } of items) {
      const isActive = link === activeLink;
      link.classList.toggle("is-active", isActive);

      if (isActive) {
        link.setAttribute("aria-current", "location");
      } else {
        link.removeAttribute("aria-current");
      }
    }
  };

  const getCurrentItem = () => {
    const headerHeight = header ? header.getBoundingClientRect().height : 72;
    const threshold = headerHeight + 40;
    let current = items[0];

    for (const item of items) {
      if (item.section.getBoundingClientRect().top <= threshold) {
        current = item;
      }
    }

    return current;
  };

  const update = () => {
    frame = 0;
    if (performance.now() < lockUntil) return;
    setActive(getCurrentItem().link);
  };

  const requestUpdate = () => {
    if (frame) return;
    frame = window.requestAnimationFrame(update);
  };

  links.forEach((link) => {
    link.addEventListener("click", () => {
      setActive(link);
      lockUntil = performance.now() + 850;

      window.setTimeout(() => {
        lockUntil = 0;
        requestUpdate();
      }, 900);
    });
  });

  window.addEventListener("scroll", requestUpdate, { passive: true });
  window.addEventListener("resize", requestUpdate);
  window.addEventListener("hashchange", () => {
    const link = links.find(
      (item) => `#${item.dataset.navTarget}` === window.location.hash
    );
    if (link) setActive(link);
    requestUpdate();
  });

  if (document.readyState === "complete") {
    update();
  } else {
    window.addEventListener("load", update, { once: true });
  }
})();
