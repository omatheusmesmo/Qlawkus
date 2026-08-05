import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import { qlawkusComposition, qlawkusTools } from 'build-time-data';
import 'echarts-force-graph';
import '@vaadin/button';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import 'qui-badge';
import 'qui-code-block';

/**
 * Composes the agent. Every capability the build resolved is shown alongside every one the manifest
 * names, because the useful question in dev mode is usually why something is missing, and a view
 * that hides absent capabilities cannot answer it.
 *
 * <p>Toggling writes the application's own agent.yml. A capability decides a Maven dependency, so
 * the edit is recorded rather than applied: the pom generator runs in generate-sources, a phase
 * quarkus:dev does not re-enter on a resource change. The page says so instead of implying the
 * change took effect.
 */
export class QwcQlawkusCapabilities extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .header {
            display: flex;
            gap: 10px;
            align-items: center;
            flex-wrap: wrap;
        }
        .posture {
            color: var(--lumo-contrast-70pct);
        }
        .location {
            color: var(--lumo-contrast-50pct);
            font-size: var(--lumo-font-size-xs);
        }
        .warning {
            color: var(--lumo-error-text-color);
        }
        .split {
            display: flex;
            gap: 20px;
            flex-wrap: wrap;
        }
        .graph {
            flex: 1 1 480px;
            min-width: 380px;
        }
        .table {
            flex: 1 1 380px;
            min-width: 340px;
        }
        .state-present {
            color: var(--lumo-success-text-color);
        }
        .state-absent {
            color: var(--lumo-error-text-color);
        }
        .state-off {
            color: var(--lumo-contrast-50pct);
        }
    `;

    static properties = {
        _composition: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._composition = null;
        this._busy = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getComposition().then(response => {
            this._composition = response.result;
        });
    }

    render() {
        if (!qlawkusComposition.manifestFound) {
            return html`
                <span class="warning">
                    No agent.yml found on the application classpath, so nothing was composed by manifest.
                </span>
            `;
        }
        return html`
            ${this._renderHeader()}
            <div class="split">
                <div class="graph">${this._renderGraph()}</div>
                <div class="table">${this._renderTable()}</div>
            </div>
            ${this._renderManifest()}
        `;
    }

    _renderHeader() {
        const writable = this._composition?.writable;
        return html`
            <div class="header">
                <span class="posture">
                    Default posture: <strong>${qlawkusComposition.defaultPosture}</strong>
                </span>
                ${qlawkusComposition.except.map(name =>
                    html`<qui-badge small><span>except: ${name}</span></qui-badge>`)}
            </div>
            ${writable === false
                ? html`<span class="warning">
                           agent.yml is not writable from here, so capabilities are read-only.
                       </span>`
                : html`<span class="location">
                           Edits are written to ${this._composition?.location ?? ''} and apply on the
                           next build - restart dev mode, or run mvn qlawkus:generate.
                       </span>`}
        `;
    }

    /**
     * Links reference nodes by array index, which is what echarts-force-graph expects.
     */
    _renderGraph() {
        const nodes = [{
            name: 'qlawkus-client',
            category: 0,
            value: 26,
            symbolSize: 26
        }];
        const links = [];

        for (const [name, state] of Object.entries(qlawkusComposition.capabilities)) {
            const category = state.present ? 1 : (state.selected ? 2 : 3);
            const size = state.present ? 18 : 12;
            nodes.push({ name: name, category: category, value: size, symbolSize: size });
            links.push({ source: 0, target: nodes.length - 1 });
        }

        const categories = ['skeleton', 'composed in', 'selected but absent', 'not selected'];

        return html`
            <echarts-force-graph width="100%" height="440px"
                edgeLength="120"
                repulsion="360"
                categories="${JSON.stringify(categories)}"
                nodes="${JSON.stringify(nodes)}"
                links="${JSON.stringify(links)}">
            </echarts-force-graph>
        `;
    }

    _renderTable() {
        const rows = Object.entries(qlawkusComposition.capabilities).map(([name, state]) => ({
            name: name,
            selected: state.selected,
            present: state.present,
            tools: qlawkusTools.filter(tool => tool.capability === name).length
        }));

        return html`
            <vaadin-grid .items="${rows}" theme="row-stripes no-border" all-rows-visible>
                <vaadin-grid-column path="name" header="Capability"></vaadin-grid-column>
                <vaadin-grid-column header="State" width="11em" flex-grow="0"
                    ${columnBodyRenderer(this._stateRenderer, [])}></vaadin-grid-column>
                <vaadin-grid-column path="tools" header="Tools" width="6em" flex-grow="0"></vaadin-grid-column>
                <vaadin-grid-column header="" width="9em" flex-grow="0"
                    ${columnBodyRenderer(this._toggleRenderer, [this._busy])}></vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _stateRenderer(row) {
        if (row.present) {
            return html`<span class="state-present">composed in</span>`;
        }
        if (row.selected) {
            return html`<span class="state-absent">selected, absent</span>`;
        }
        return html`<span class="state-off">not selected</span>`;
    }

    _toggleRenderer(row) {
        const enable = !row.selected;
        return html`
            <vaadin-button theme="small" ?disabled="${this._busy || !this._composition?.writable}"
                           @click="${() => this._toggle(row.name, enable)}">
                ${enable ? 'Select' : 'Deselect'}
            </vaadin-button>
        `;
    }

    _renderManifest() {
        if (!this._composition?.yaml) {
            return html``;
        }
        return html`
            <qui-code-block mode="yaml" content="${this._composition.yaml}"></qui-code-block>
        `;
    }

    _toggle(capability, enabled) {
        this._busy = true;
        this.jsonRpc.setCapability({ capability: capability, enabled: enabled }).then(() => {
            notifier.showSuccessMessage(
                `${capability} ${enabled ? 'selected' : 'deselected'} in agent.yml. `
                + `Restart dev mode (or run mvn qlawkus:generate) to apply it.`);
            this.hotReload();
        }).catch(error => {
            notifier.showErrorMessage(`Could not update ${capability}: ${error.message ?? error}`);
        }).finally(() => {
            this._busy = false;
        });
    }
}

customElements.define('qwc-qlawkus-capabilities', QwcQlawkusCapabilities);
