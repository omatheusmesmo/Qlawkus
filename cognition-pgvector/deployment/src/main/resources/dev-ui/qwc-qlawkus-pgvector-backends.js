import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/grid';
import '@vaadin/progress-bar';
import 'qui-alert';

/**
 * Shows which implementation each cognition SPI resolved to, and runs the two operations that move
 * data between the markdown files and pgvector.
 *
 * <p>The backend list is worth showing because no single config value answers "am I on Postgres?":
 * the markdown stores are the default beans, so the answer depends on the build-time switch and on
 * whether this extension is on the classpath at all.
 */
export class QwcQlawkusPgvectorBackends extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            padding: 15px;
        }
        .actions {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            align-items: center;
        }
        .pg {
            color: var(--lumo-success-text-color);
        }
        .files {
            color: var(--lumo-contrast-70pct);
        }
    `;

    static properties = {
        _backends: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._backends = null;
        this._busy = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getBackends().then(response => {
            this._backends = response.result;
        });
    }

    render() {
        if (this._backends === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        return html`
            ${this._renderBackends()}
            ${this._renderActions()}
            <qui-alert level="warning" size="small">
                <span>Migrate copies one way and overwrites the destination persona and owner
                      profile. Reconcile is the non-destructive union.</span>
            </qui-alert>
        `;
    }

    _renderBackends() {
        const rows = Object.entries(this._backends).map(([spi, impl]) => ({ spi, impl }));
        return html`
            <vaadin-grid .items="${rows}" theme="row-stripes no-border" all-rows-visible>
                <vaadin-grid-column path="spi" header="SPI"></vaadin-grid-column>
                <vaadin-grid-column path="impl" header="Resolved implementation"></vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _renderActions() {
        return html`
            <div class="actions">
                <vaadin-button theme="small primary" ?disabled="${this._busy}"
                               @click="${() => this._run('Reconcile', 'reconcile')}">
                    Reconcile
                </vaadin-button>
                <vaadin-button theme="small" ?disabled="${this._busy}"
                               @click="${() => this._run('Migrate files to pg', 'migrateFilesToPg')}">
                    Migrate files → pg
                </vaadin-button>
                <vaadin-button theme="small" ?disabled="${this._busy}"
                               @click="${() => this._run('Migrate pg to files', 'migratePgToFiles')}">
                    Migrate pg → files
                </vaadin-button>
            </div>
        `;
    }

    _run(label, method) {
        this._busy = true;
        notifier.showInfoMessage(`${label} started`);
        this.jsonRpc[method]().then(response => {
            const stats = response.result;
            notifier.showSuccessMessage(
                `${label}: +${stats.toPg} to pg, +${stats.toFiles} to files`);
            this.hotReload();
        }).catch(error => {
            notifier.showErrorMessage(`${label} failed: ${error.message ?? error}`);
        }).finally(() => {
            this._busy = false;
        });
    }
}

customElements.define('qwc-qlawkus-pgvector-backends', QwcQlawkusPgvectorBackends);
