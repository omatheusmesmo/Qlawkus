import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { qlawkusTools } from 'build-time-data';
import '@vaadin/grid';
import '@vaadin/progress-bar';
import 'qui-badge';

/**
 * Lists the @QlawTool beans and attributes each to the capability that plugged it. Two sources are
 * compared on purpose: the build-time Jandex scan (what the build wired) and the live CDI registry
 * (what the container actually holds). They should agree, and the page says so plainly when they do
 * not - that mismatch is the interesting failure, since it means a tool was scanned but never became
 * a usable bean.
 */
export class QwcQlawkusTools extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .status {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .tools {
            flex: 1;
            min-height: 200px;
        }
        .mismatch {
            color: var(--lumo-error-text-color);
        }
        .skeleton {
            color: var(--lumo-contrast-50pct);
        }
    `;

    static properties = {
        _registered: { state: true }
    };

    constructor() {
        super();
        this._registered = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getRegisteredTools().then(response => {
            this._registered = response.result;
        });
    }

    render() {
        if (this._registered === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        return html`
            ${this._renderStatus()}
            ${this._renderTools()}
        `;
    }

    _renderStatus() {
        const registeredNames = new Set(this._registered.map(tool => tool.className));
        const missing = qlawkusTools.filter(tool => !registeredNames.has(tool.className));

        if (missing.length === 0) {
            return html`
                <div class="status">
                    <span>${qlawkusTools.length} tool(s) scanned at build time, all registered in the container.</span>
                </div>
            `;
        }
        return html`
            <div class="status mismatch">
                <span>${missing.length} scanned tool(s) are not registered:
                      ${missing.map(tool => tool.simpleName).join(', ')}</span>
            </div>
        `;
    }

    _renderTools() {
        const registeredNames = new Set(this._registered.map(tool => tool.className));
        const rows = qlawkusTools.map(tool => ({
            simpleName: tool.simpleName,
            capability: tool.capability,
            className: tool.className,
            registered: registeredNames.has(tool.className) ? 'yes' : 'no'
        }));

        return html`
            <vaadin-grid class="tools" .items="${rows}" theme="row-stripes no-border">
                <vaadin-grid-column path="simpleName" header="Tool" width="18em"></vaadin-grid-column>
                <vaadin-grid-column path="capability" header="Plugged by" width="14em"></vaadin-grid-column>
                <vaadin-grid-column path="registered" header="Registered" width="9em" flex-grow="0"></vaadin-grid-column>
                <vaadin-grid-column path="className" header="Class"></vaadin-grid-column>
            </vaadin-grid>
        `;
    }
}

customElements.define('qwc-qlawkus-tools', QwcQlawkusTools);
