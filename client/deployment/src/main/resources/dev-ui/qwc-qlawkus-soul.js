import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/progress-bar';
import 'qui-card';
import 'qui-badge';

/**
 * Shows the two singletons SoulEngine injects into every system message: the agent's persona and the
 * owner profile it serves. Read straight from the live stores, so the page reflects what the next
 * turn will actually be told.
 */
export class QwcQlawkusSoul extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 20px;
            padding: 15px;
        }
        .field {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding-bottom: 12px;
        }
        .label {
            font-size: var(--lumo-font-size-xs);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-contrast-50pct);
        }
        .value {
            white-space: pre-wrap;
            line-height: 1.5;
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
        .header {
            display: flex;
            align-items: center;
            gap: 10px;
        }
    `;

    static properties = {
        _soul: { state: true },
        _profile: { state: true }
    };

    constructor() {
        super();
        this._soul = null;
        this._profile = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getSoul().then(response => {
            this._soul = response.result;
        });
        this.jsonRpc.getUserProfile().then(response => {
            this._profile = response.result;
        });
    }

    render() {
        if (this._soul === null || this._profile === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        return html`
            ${this._renderSoul()}
            ${this._renderProfile()}
        `;
    }

    _renderSoul() {
        return html`
            <qui-card title="Soul">
                <div slot="content">
                    <div class="header">
                        <span class="value"><strong>${this._soul.name}</strong></span>
                        ${this._soul.mood
                            ? html`<qui-badge small><span>${this._soul.mood}</span></qui-badge>`
                            : ''}
                    </div>
                    ${this._field('Core identity', this._soul.coreIdentity)}
                    ${this._field('Current state', this._soul.currentState)}
                    ${this._field('Updated at', this._soul.updatedAt)}
                </div>
            </qui-card>
        `;
    }

    _renderProfile() {
        return html`
            <qui-card title="Owner profile">
                <div slot="content">
                    ${this._field('Name', this._profile.name)}
                    ${this._field('Profile', this._profile.profile)}
                    ${this._field('Updated at', this._profile.updatedAt)}
                </div>
            </qui-card>
        `;
    }

    _field(label, value) {
        return html`
            <div class="field">
                <span class="label">${label}</span>
                ${value
                    ? html`<span class="value">${value}</span>`
                    : html`<span class="value empty">not set</span>`}
            </div>
        `;
    }
}

customElements.define('qwc-qlawkus-soul', QwcQlawkusSoul);
