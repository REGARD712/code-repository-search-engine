const PAGE_SIZE = 10;

const elements = {
  form: document.querySelector('#search-form'),
  input: document.querySelector('#search-input'),
  summary: document.querySelector('#summary'),
  totalCount: document.querySelector('#total-count'),
  summaryMeta: document.querySelector('#summary-meta'),
  tokenList: document.querySelector('#token-list'),
  loading: document.querySelector('#loading'),
  errorPanel: document.querySelector('#error-panel'),
  errorMessage: document.querySelector('#error-message'),
  retryButton: document.querySelector('#retry-button'),
  emptyPanel: document.querySelector('#empty-panel'),
  emptyTitle: document.querySelector('#empty-title'),
  emptyCopy: document.querySelector('#empty-copy'),
  results: document.querySelector('#results'),
  pagination: document.querySelector('#pagination'),
  pageSummary: document.querySelector('#page-summary'),
  pageNumbers: document.querySelector('#page-numbers'),
  prevPage: document.querySelector('#prev-page'),
  nextPage: document.querySelector('#next-page'),
  template: document.querySelector('#result-card-template'),
};

const state = {
  query: '',
  results: [],
  tokens: [],
  total: 0,
  elapsedMs: 0,
  page: 1,
  requestId: 0,
  controller: null,
};

const numberFormatter = new Intl.NumberFormat('zh-CN');

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;',
  }[character]));
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * 先按原始文本切分，再对非匹配片段做 HTML 转义，
 * 避免高亮结果被 README 中的 HTML/Markdown 特殊字符破坏。
 */
function highlightReadme(text, tokens) {
  const value = typeof text === 'string' ? text : '';
  const validTokens = [...new Set(
    tokens
      .map((token) => String(token || '').trim())
      .filter((token) => token.length > 0),
  )].sort((left, right) => right.length - left.length);

  if (!validTokens.length || !value) {
    return escapeHtml(value);
  }

  const pattern = new RegExp(validTokens.map(escapeRegExp).join('|'), 'gi');
  let result = '';
  let lastIndex = 0;

  for (const match of value.matchAll(pattern)) {
    result += escapeHtml(value.slice(lastIndex, match.index));
    result += `<mark>${escapeHtml(match[0])}</mark>`;
    lastIndex = match.index + match[0].length;
  }
  result += escapeHtml(value.slice(lastIndex));
  return result;
}

function hideAllStates() {
  elements.loading.hidden = true;
  elements.errorPanel.hidden = true;
  elements.emptyPanel.hidden = true;
}

function showLoading() {
  hideAllStates();
  elements.summary.hidden = true;
  elements.results.hidden = true;
  elements.pagination.hidden = true;
  elements.loading.hidden = false;
}

function showIdle() {
  hideAllStates();
  elements.summary.hidden = true;
  elements.results.hidden = true;
  elements.pagination.hidden = true;
  elements.emptyTitle.textContent = '输入关键词开始搜索';
  elements.emptyCopy.textContent = '例如：数据结构、大模型、Spring 或权限管理。';
  elements.emptyPanel.hidden = false;
}

function showError(message) {
  hideAllStates();
  elements.summary.hidden = true;
  elements.results.hidden = true;
  elements.pagination.hidden = true;
  elements.errorMessage.textContent = message;
  elements.errorPanel.hidden = false;
}

function renderTokens() {
  elements.tokenList.replaceChildren();
  if (state.tokens.length === 0) {
    elements.tokenList.hidden = true;
    return;
  }

  elements.tokenList.hidden = false;
  const fragment = document.createDocumentFragment();
  state.tokens.forEach((token) => {
    const chip = document.createElement('span');
    chip.className = 'token';
    chip.textContent = token;
    fragment.appendChild(chip);
  });
  elements.tokenList.appendChild(fragment);
}

function renderSummary() {
  elements.summary.hidden = false;
  elements.totalCount.textContent = numberFormatter.format(state.total);
  const timeText = state.elapsedMs > 0 ? ` · 检索耗时 ${state.elapsedMs} ms` : '';
  elements.summaryMeta.textContent = `关键词「${state.query}」${timeText}`;
  renderTokens();
}

function renderResultCard(item, index) {
  const card = elements.template.content.firstElementChild.cloneNode(true);
  const name = card.querySelector('.repo-name');
  const url = card.querySelector('.repo-url');
  const id = card.querySelector('.repo-id');
  const readme = card.querySelector('.repo-readme');

  card.querySelector('.repo-index').textContent = String(index + 1).padStart(2, '0');

  const fullName = item.fullName || '未命名仓库';
  name.textContent = fullName;
  name.title = fullName;
  if (item.htmlUrl) {
    name.href = item.htmlUrl;
  } else {
    name.removeAttribute('href');
    name.removeAttribute('target');
  }

  if (item.htmlUrl) {
    url.textContent = item.htmlUrl;
    url.href = item.htmlUrl;
    url.title = item.htmlUrl;
  } else {
    url.textContent = '暂无仓库地址';
    url.removeAttribute('href');
    url.removeAttribute('target');
  }

  id.textContent = `#${item.id ?? '-'}`;
  id.title = `Database ID: ${item.id ?? '-'} · Gitee ID: ${item.repositoryId ?? '-'}`;

  const readmeText = typeof item.readme === 'string' ? item.readme : '';
  if (readmeText.trim()) {
    readme.innerHTML = highlightReadme(readmeText, state.tokens);
    readme.classList.remove('is-empty');

    // README 较长时，把阅读位置滚到第一个命中词，避免高亮内容藏在下方。
    const firstMark = readme.querySelector('mark');
    if (firstMark) {
      requestAnimationFrame(() => {
        readme.scrollTop = Math.max(0, firstMark.offsetTop - readme.offsetTop - 16);
      });
    }
  } else {
    readme.textContent = '该仓库暂无 README 内容。';
    readme.classList.add('is-empty');
  }

  return card;
}

function renderPagination(pageCount) {
  if (state.total <= PAGE_SIZE) {
    elements.pagination.hidden = true;
    return;
  }

  elements.pagination.hidden = false;
  elements.pageSummary.textContent = `第 ${state.page} / ${pageCount} 页`;
  elements.prevPage.disabled = state.page <= 1;
  elements.nextPage.disabled = state.page >= pageCount;

  const pageSet = new Set([1, pageCount, state.page - 1, state.page, state.page + 1]);
  const pages = pageCount <= 7
    ? Array.from({ length: pageCount }, (_, index) => index + 1)
    : [...pageSet]
      .filter((page) => page >= 1 && page <= pageCount)
      .sort((left, right) => left - right);

  const fragment = document.createDocumentFragment();
  let previousPage = 0;

  pages.forEach((page) => {
    if (previousPage && page - previousPage > 1) {
      const ellipsis = document.createElement('span');
      ellipsis.className = 'page-ellipsis';
      ellipsis.textContent = '…';
      fragment.appendChild(ellipsis);
    }

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'page-number';
    button.textContent = String(page);
    button.dataset.page = String(page);
    button.setAttribute('aria-label', `第 ${page} 页`);
    if (page === state.page) {
      button.classList.add('is-current');
      button.setAttribute('aria-current', 'page');
    }
    fragment.appendChild(button);
    previousPage = page;
  });

  elements.pageNumbers.replaceChildren(fragment);
}

function renderResults() {
  const pageCount = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
  state.page = Math.min(Math.max(state.page, 1), pageCount);

  hideAllStates();
  renderSummary();

  if (state.total === 0) {
    elements.results.hidden = true;
    elements.pagination.hidden = true;
    elements.emptyTitle.textContent = '没有找到相关仓库';
    elements.emptyCopy.textContent = `没有仓库同时命中「${state.query}」的关键词和语义结果，换个关键词试试。`;
    elements.emptyPanel.hidden = false;
    return;
  }

  const start = (state.page - 1) * PAGE_SIZE;
  const pageItems = state.results.slice(start, start + PAGE_SIZE);
  const fragment = document.createDocumentFragment();
  pageItems.forEach((item, index) => {
    fragment.appendChild(renderResultCard(item, start + index));
  });

  elements.results.replaceChildren(fragment);
  elements.results.hidden = false;
  renderPagination(pageCount);
}

function updateUrl(query) {
  const url = new URL(window.location.href);
  if (query) {
    url.searchParams.set('query', query);
  } else {
    url.searchParams.delete('query');
  }
  window.history.replaceState(null, '', url);
}

async function performSearch(rawQuery, options = {}) {
  const query = String(rawQuery || '').trim();
  if (!query) {
    state.query = '';
    state.results = [];
    state.total = 0;
    state.tokens = [];
    state.page = 1;
    if (options.updateUrl !== false) {
      updateUrl('');
    }
    showIdle();
    elements.input.focus();
    return;
  }

  if (state.controller) {
    state.controller.abort();
  }

  const controller = new AbortController();
  const requestId = ++state.requestId;
  state.controller = controller;
  state.query = query;
  state.page = 1;
  elements.input.value = query;

  if (options.updateUrl !== false) {
    updateUrl(query);
  }
  showLoading();

  try {
    const response = await fetch(`/api/search?query=${encodeURIComponent(query)}`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    const payload = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(payload.message || `请求失败（HTTP ${response.status}）`);
    }
    if (requestId !== state.requestId) {
      return;
    }

    state.results = Array.isArray(payload.results) ? payload.results : [];
    state.tokens = Array.isArray(payload.tokens) ? payload.tokens : [];
    state.total = Number.isFinite(payload.total) ? payload.total : state.results.length;
    state.elapsedMs = Number.isFinite(payload.elapsedMs) ? payload.elapsedMs : 0;
    state.page = 1;
    renderResults();
  } catch (error) {
    if (error.name === 'AbortError' || requestId !== state.requestId) {
      return;
    }
    showError(error.message || '网络请求失败，请确认后端服务已启动。');
  }
}

function goToPage(page) {
  const pageCount = Math.max(1, Math.ceil(state.total / PAGE_SIZE));
  const nextPage = Math.min(Math.max(page, 1), pageCount);
  if (nextPage === state.page) {
    return;
  }
  state.page = nextPage;
  renderResults();
  elements.results.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

elements.form.addEventListener('submit', (event) => {
  event.preventDefault();
  performSearch(elements.input.value);
});

elements.retryButton.addEventListener('click', () => {
  performSearch(state.query || elements.input.value, { updateUrl: false });
});

elements.prevPage.addEventListener('click', () => goToPage(state.page - 1));
elements.nextPage.addEventListener('click', () => goToPage(state.page + 1));

elements.pageNumbers.addEventListener('click', (event) => {
  const button = event.target.closest('[data-page]');
  if (button) {
    goToPage(Number(button.dataset.page));
  }
});

document.querySelectorAll('.quick-chip').forEach((button) => {
  button.addEventListener('click', () => {
    elements.input.value = button.dataset.query;
    performSearch(button.dataset.query);
  });
});

const initialQuery = new URLSearchParams(window.location.search).get('query') || '';
if (initialQuery.trim()) {
  elements.input.value = initialQuery;
  performSearch(initialQuery, { updateUrl: false });
} else {
  showIdle();
}
