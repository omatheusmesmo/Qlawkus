import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import '@vaadin/progress-bar';

/**
 * Lists what the agent has recorded as career achievements, so the passive extraction can be seen
 * working without exporting the document.
 */
export class QwcQlawkusBrag extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .entries {
            flex: 1;
            min-height: 200px;
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
    `;

    static properties = {
        _entries: { state: true }
    };

    constructor() {
        super();
        this._entries = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getEntries().then(response => {
            this._entries = response.result;
        });
    }

    render() {
        if (this._entries === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        if (this._entries.length === 0) {
            return html`<span class="empty">
                            Nothing recorded yet. Entries appear as the agent notices achievements
                            in conversation.
                        </span>`;
        }
        return html`
            <vaadin-grid class="entries" .items="${this._entries}" theme="row-stripes no-border">
                <vaadin-grid-column path="date" header="Date" width="9em" flex-grow="0"></vaadin-grid-column>
                <vaadin-grid-column path="achievement" header="Achievement"></vaadin-grid-column>
                <vaadin-grid-column path="impact" header="Impact"></vaadin-grid-column>
                <vaadin-grid-column path="repo" header="Repo" width="12em"></vaadin-grid-column>
            </vaadin-grid>
        `;
    }
}

customElements.define('qwc-qlawkus-brag', QwcQlawkusBrag);
