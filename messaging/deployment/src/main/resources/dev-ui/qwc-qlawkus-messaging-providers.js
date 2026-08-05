import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/progress-bar';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

/**
 * Shows every messaging adapter on the classpath and whether it actually registered. The two differ
 * whenever a provider is disabled by config, which is the usual explanation for a bot that is
 * deployed but silent.
 */
export class QwcQlawkusMessagingProviders extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .providers {
            flex: 0 0 auto;
        }
        .test {
            display: flex;
            gap: 10px;
            align-items: baseline;
            flex-wrap: wrap;
        }
        .active {
            color: var(--lumo-success-text-color);
        }
        .inactive {
            color: var(--lumo-contrast-50pct);
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
    `;

    static properties = {
        _providers: { state: true },
        _chatId: { state: true },
        _text: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._providers = null;
        this._chatId = '';
        this._text = '';
        this._busy = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getProviders().then(response => {
            this._providers = response.result;
        });
    }

    render() {
        if (this._providers === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        if (this._providers.length === 0) {
            return html`<span class="empty">
                            No messaging adapters on the classpath. Add one (Discord, Telegram, ...)
                            to this application to see it here.
                        </span>`;
        }
        return html`
            <vaadin-grid class="providers" .items="${this._providers}"
                         theme="row-stripes no-border" all-rows-visible>
                <vaadin-grid-column path="providerId" header="Provider" width="12em"></vaadin-grid-column>
                <vaadin-grid-column path="adapter" header="Adapter"></vaadin-grid-column>
                <vaadin-grid-column path="format" header="Format" width="12em"></vaadin-grid-column>
                <vaadin-grid-column header="State" width="10em" flex-grow="0"
                    ${columnBodyRenderer(this._stateRenderer, [])}></vaadin-grid-column>
                <vaadin-grid-column header="" width="8em" flex-grow="0"
                    ${columnBodyRenderer(this._testRenderer, [this._busy, this._chatId])}></vaadin-grid-column>
            </vaadin-grid>
            ${this._renderTestFields()}
        `;
    }

    _stateRenderer(row) {
        return row.active
            ? html`<span class="active">active</span>`
            : html`<span class="inactive">disabled</span>`;
    }

    _testRenderer(row) {
        return html`
            <vaadin-button theme="small"
                           ?disabled="${this._busy || !row.active || !this._chatId}"
                           @click="${() => this._send(row.providerId)}">
                Send
            </vaadin-button>
        `;
    }

    _renderTestFields() {
        return html`
            <div class="test">
                <vaadin-text-field label="Chat id" placeholder="destination"
                                   .value="${this._chatId}"
                                   @value-changed="${e => this._chatId = e.detail.value}">
                </vaadin-text-field>
                <vaadin-text-field label="Message" placeholder="Ping from the Qlawkus Dev UI"
                                   .value="${this._text}"
                                   @value-changed="${e => this._text = e.detail.value}">
                </vaadin-text-field>
            </div>
        `;
    }

    _send(providerId) {
        this._busy = true;
        this.jsonRpc.sendTestMessage({
            providerId: providerId,
            chatId: this._chatId,
            text: this._text
        }).then(response => {
            notifier.showSuccessMessage(response.result);
        }).catch(error => {
            notifier.showErrorMessage(`Send failed: ${error.message ?? error}`);
        }).finally(() => {
            this._busy = false;
        });
    }
}

customElements.define('qwc-qlawkus-messaging-providers', QwcQlawkusMessagingProviders);
