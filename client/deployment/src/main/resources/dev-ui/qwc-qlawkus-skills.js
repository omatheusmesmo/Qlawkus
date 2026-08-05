import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/grid';
import '@vaadin/progress-bar';

/**
 * Shows the skill index the way the agent sees it - name plus description, the same pair
 * SkillIndexRenderer injects every turn - and runs the two skill jobs on demand.
 */
export class QwcQlawkusSkills extends QwcHotReloadElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 15px;
            height: 100%;
            padding: 15px;
        }
        .actions {
            display: flex;
            gap: 10px;
            align-items: center;
            flex-wrap: wrap;
        }
        .count {
            color: var(--lumo-contrast-50pct);
        }
        .skills {
            flex: 1;
            min-height: 200px;
        }
        .empty {
            color: var(--lumo-contrast-50pct);
            font-style: italic;
        }
    `;

    static properties = {
        _skills: { state: true },
        _busy: { state: true }
    };

    constructor() {
        super();
        this._skills = null;
        this._busy = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.hotReload();
    }

    hotReload() {
        this.jsonRpc.getSkills().then(response => {
            this._skills = response.result;
        });
    }

    render() {
        if (this._skills === null) {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
        return html`
            <div class="actions">
                <vaadin-button theme="small" ?disabled="${this._busy}"
                               title="Remove skills made redundant by others"
                               @click="${() => this._run('Curate', 'curateSkills')}">
                    Curate
                </vaadin-button>
                <vaadin-button theme="small" ?disabled="${this._busy}"
                               title="Age unused skills through active, stale and archived"
                               @click="${() => this._run('Lifecycle sweep', 'sweepSkillLifecycle')}">
                    Lifecycle sweep
                </vaadin-button>
                <span class="count">${this._skills.length} skill(s) in the injected index</span>
            </div>
            ${this._renderSkills()}
        `;
    }

    _renderSkills() {
        if (this._skills.length === 0) {
            return html`<span class="empty">No skills yet. The agent writes them as it learns procedures.</span>`;
        }
        return html`
            <vaadin-grid class="skills" .items="${this._skills}" theme="row-stripes no-border">
                <vaadin-grid-column path="name" header="Name" width="16em"></vaadin-grid-column>
                <vaadin-grid-column path="description" header="Description"></vaadin-grid-column>
            </vaadin-grid>
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
}

customElements.define('qwc-qlawkus-skills', QwcQlawkusSkills);
