import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/grid';
import '@vaadin/progress-bar';
import 'qui-badge';

/**
 * Browses the declarative memory and runs its background jobs on demand. The job buttons call the
 * same manual-run methods the /api/admin/memory endpoints expose, so a run here is indistinguishable
 * from one triggered in production.
 */
export class QwcQlawkusMemory extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .summary {
            display: flex;
            gap: 25px;
            flex-wrap: wrap;
            align-items: center;
        }
        .metric {
            display: flex;
            flex-direction: column;
        }
        .metric .label {
            font-size: var(--lumo-font-size-xs);
            text-transform: uppercase;
            color: var(--lumo-contrast-50pct);
        }
        .metric .number {
            font-size: var(--lumo-font-size-xl);
        }
        .sources {
            display: flex;
            gap: 6px;
            flex-wrap: wrap;
        }
        .actions {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        .facts {
            flex: 1;
            min-height: 200px;
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
    `;

    static properties = {
        _summary: { state: true },
        _facts: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._summary = null;
        this._facts = null;
        this._busy = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getMemorySummary().then(response => {
            this._summary = response.result;
        });
        this.jsonRpc.getFacts({ limit: 50 }).then(response => {
            this._facts = response.result;
        });
    }

    render() {
        if (this._summary === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        return html`
            ${this._renderSummary()}
            ${this._renderActions()}
            ${this._renderFacts()}
        `;
    }

    _renderSummary() {
        return html`
            <div class="summary">
                <div class="metric">
                    <span class="label">Journals</span>
                    <span class="number">${this._summary.journalCount}</span>
                </div>
                <div class="metric">
                    <span class="label">Chat messages</span>
                    <span class="number">${this._summary.chatMessageCount}</span>
                </div>
                <div class="metric">
                    <span class="label">Embedding sources</span>
                    <span class="sources">
                        ${this._summary.embeddingSources.length === 0
                            ? html`<span class="empty">none</span>`
                            : this._summary.embeddingSources.map(source =>
                                html`<qui-badge small><span>${source}</span></qui-badge>`)}
                    </span>
                </div>
            </div>
        `;
    }

    _renderActions() {
        return html`
            <div class="actions">
                ${this._action('Review', 'reviewMemory', 'Deduplicate near-identical facts')}
                ${this._action('Curate', 'curateProfile', 'Fold facts into the owner profile')}
                ${this._action('Consolidate', 'consolidateEpisodes', 'Summarize the day into a journal')}
            </div>
        `;
    }

    _action(label, method, tooltip) {
        return html`
            <vaadin-button theme="small" title="${tooltip}" ?disabled="${this._busy}"
                           @click="${() => this._run(label, method)}">
                ${label}
            </vaadin-button>
        `;
    }

    _run(label, method) {
        this._busy = true;
        notifier.showInfoMessage(`${label} started`);
        this.jsonRpc[method]().then(response => {
            notifier.showSuccessMessage(`${label} finished: ${response.result}`);
            this.hotReload();
        }).catch(error => {
            notifier.showErrorMessage(`${label} failed: ${error.message ?? error}`);
        }).finally(() => {
            this._busy = false;
        });
    }

    _renderFacts() {
        if (this._facts === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        if (this._facts.length === 0) {
            return html`<span class="empty">No facts stored yet.</span>`;
        }
        return html`
            <vaadin-grid class="facts" .items="${this._facts.map((text, index) => ({ index: index + 1, text }))}"
                         theme="row-stripes no-border">
                <vaadin-grid-column path="index" header="#" width="4em" flex-grow="0"></vaadin-grid-column>
                <vaadin-grid-column path="text" header="Fact"></vaadin-grid-column>
            </vaadin-grid>
        `;
    }
}

customElements.define('qwc-qlawkus-memory', QwcQlawkusMemory);
