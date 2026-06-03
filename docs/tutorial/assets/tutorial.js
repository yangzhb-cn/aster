// 教程页共享交互：测验、聊天演示、流程步骤和术语提示。
document.addEventListener("DOMContentLoaded", () => {
  initNavSearch();
  initQuiz();
  initChat();
  initFlow();
  initTerms();
});

function initNavSearch() {
  const nav = document.querySelector(".nav-links");
  if (!nav || document.querySelector(".nav-search")) {
    return;
  }

  const search = document.createElement("input");
  search.className = "nav-search";
  search.type = "search";
  search.placeholder = "Search";
  search.setAttribute("aria-label", "搜索教程页面");
  nav.before(search);

  const links = Array.from(nav.querySelectorAll("a"));
  search.addEventListener("input", () => {
    const query = search.value.trim().toLowerCase();
    links.forEach((link) => {
      const text = link.textContent.trim().toLowerCase();
      link.classList.toggle("nav-hidden", Boolean(query) && !text.includes(query));
    });
  });
}

function initQuiz() {
  document.querySelectorAll(".quiz").forEach((quiz) => {
    quiz.querySelectorAll(".quiz-option").forEach((button) => {
      button.addEventListener("click", () => {
        quiz.querySelectorAll(".quiz-option").forEach((item) => item.classList.remove("selected"));
        button.classList.add("selected");
      });
    });

    const check = quiz.querySelector("[data-check-quiz]");
    if (!check) {
      return;
    }
    check.addEventListener("click", () => {
      const selected = quiz.querySelector(".quiz-option.selected");
      const feedback = quiz.querySelector(".quiz-feedback");
      if (!selected) {
        feedback.textContent = "先选一个答案。";
        return;
      }

      const isRight = selected.dataset.value === quiz.dataset.answer;
      selected.classList.add(isRight ? "correct" : "wrong");
      feedback.textContent = isRight ? quiz.dataset.right : quiz.dataset.wrong;
    });
  });
}

function initChat() {
  document.querySelectorAll(".chat").forEach((chat) => {
    const messages = Array.from(chat.querySelectorAll(".chat-message"));
    let index = 0;

    const showNext = () => {
      if (index < messages.length) {
        messages[index].classList.add("show");
        index += 1;
      }
    };

    const reset = () => {
      messages.forEach((message) => message.classList.remove("show"));
      index = 0;
    };

    chat.querySelector("[data-chat-next]")?.addEventListener("click", showNext);
    chat.querySelector("[data-chat-all]")?.addEventListener("click", () => {
      messages.forEach((message) => message.classList.add("show"));
      index = messages.length;
    });
    chat.querySelector("[data-chat-reset]")?.addEventListener("click", reset);
  });
}

function initFlow() {
  document.querySelectorAll(".flow").forEach((flow) => {
    const nodes = Array.from(flow.querySelectorAll(".flow-node"));
    const steps = JSON.parse(flow.dataset.steps || "[]");
    const status = flow.querySelector(".flow-status");
    let index = -1;

    const render = () => {
      nodes.forEach((node) => node.classList.remove("active"));
      const step = steps[index];
      if (!step) {
        status.textContent = "点击下一步开始。";
        return;
      }
      flow.querySelector(`[data-node="${step.node}"]`)?.classList.add("active");
      status.textContent = step.text;
    };

    flow.querySelector("[data-flow-next]")?.addEventListener("click", () => {
      index = Math.min(index + 1, steps.length - 1);
      render();
    });
    flow.querySelector("[data-flow-reset]")?.addEventListener("click", () => {
      index = -1;
      render();
    });
    render();
  });
}

function initTerms() {
  let tooltip = null;
  document.querySelectorAll(".term").forEach((term) => {
    term.addEventListener("mouseenter", () => showTooltip(term));
    term.addEventListener("mouseleave", hideTooltip);
    term.addEventListener("click", () => showTooltip(term));
  });

  function showTooltip(term) {
    hideTooltip();
    tooltip = document.createElement("div");
    tooltip.className = "tooltip";
    tooltip.textContent = term.dataset.tip;
    document.body.appendChild(tooltip);

    const rect = term.getBoundingClientRect();
    tooltip.style.left = Math.min(rect.left, window.innerWidth - 300) + "px";
    tooltip.style.top = rect.bottom + 8 + "px";
  }

  function hideTooltip() {
    tooltip?.remove();
    tooltip = null;
  }
}
