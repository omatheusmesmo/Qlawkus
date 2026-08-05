import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/progress-bar';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

/**
 * Searches the remote skill registry and installs a hit into the owned skill root, where it enters
 * the injected index on the next turn.
 */
export class QwcQlawkusSkillHub extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .search {
            display: flex;
            gap: 10px;
            align-items: baseline;
        }
        .field {
            flex: 1;
            max-width: 460px;
        }
        .results {
            flex: 1;
            min-height: 200px;
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
    `;

    static properties = {
        _query: { state: true },
        _results: { state: true },
        _searching: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._query = '';
        this._results = null;
        this._searching = false;
        this._busy = false;
    }

    hotReload() {
        // Results come from a remote registry, so a hot reload should not silently re-issue a query.
        this._results = null;
    }

    render() {
        return html`
            <div class="search">
                <vaadin-text-field class="field" label="Search the hub"
                                   placeholder="e.g. changelog, code review"
                                   .value="${this._query}"
                                   @value-changed="${e => this._query = e.detail.value}"
                                   @keydown="${e => e.key === 'Enter' ? this._search() : null}">
                </vaadin-text-field>
                <vaadin-button theme="primary" ?disabled="${this._searching}"
                               @click="${() => this._search()}">
                    Search
                </vaadin-button>
            </div>
            ${this._searching ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : ''}
            ${this._renderResults()}
        `;
    }

    _renderResults() {
        if (this._results === null) {
            return html`<span class="empty">Search the registry to see skills you can install.</span>`;
        }
        if (this._results.length === 0) {
            return html`<span class="empty">No skills matched.</span>`;
        }
        return html`
            <vaadin-grid class="results" .items="${this._results}" theme="row-stripes no-border">
                <vaadin-grid-column path="name" header="Skill" width="16em"></vaadin-grid-column>
                <vaadin-grid-column path="description" header="Description"></vaadin-grid-column>
                <vaadin-grid-column path="source" header="Source" width="18em"></vaadin-grid-column>
                <vaadin-grid-column header="" width="8em" flex-grow="0"
                    ${columnBodyRenderer(this._installRenderer, [this._busy])}></vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _installRenderer(row) {
        return html`
            <vaadin-button theme="small" ?disabled="${this._busy}"
                           @click="${() => this._install(row.source)}">
                Install
            </vaadin-button>
        `;
    }

    _search() {
        if (!this._query.trim()) {
            return;
        }
        this._searching = true;
        this.jsonRpc.search({ query: this._query, limit: 20 }).then(response => {
            this._results = response.result;
        }).catch(error => {
            notifier.showErrorMessage(`Search failed: ${error.message ?? error}`);
        }).finally(() => {
            this._searching = false;
        });
    }

    _install(source) {
        this._busy = true;
        notifier.showInfoMessage(`Installing ${source}`);
        this.jsonRpc.install({ source: source }).then(response => {
            notifier.showSuccessMessage(
                `Installed ${response.result.name} - it enters the injected index next turn`);
        }).catch(error => {
            notifier.showErrorMessage(`Install failed: ${error.message ?? error}`);
        }).finally(() => {
            this._busy = false;
        });
    }
}

customElements.define('qwc-qlawkus-skill-hub', QwcQlawkusSkillHub);
