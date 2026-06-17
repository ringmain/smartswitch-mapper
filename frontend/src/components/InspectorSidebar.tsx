import React from 'react';
import { checkInboundDialect } from '../hooks/useMappingEngine';
import type { CommonElement, LeafParams } from '../hooks/useMappingEngine';

interface InspectorSidebarProps {
    selectedItemType: 'ctrx' | 'spdh' | 'outbound' | null;
    selectedSpdhTag: CommonElement | null;
    selectedOutField: CommonElement | null;
    selectedCtrxPath: string | null;
    editingParams: LeafParams | null;
    editingDesc: string;
    isSaving: boolean;
    lineageLinks: any[];
    outboundLinks: any[];
    setEditingDesc: (desc: string) => void;
    setEditingParams: (params: any) => void;
    handleClearStates: () => void;
    handleSpdhOrOutboundSave: (e: React.SyntheticEvent) => void;
    handleCtrxSave: (e: React.SyntheticEvent) => void;
    deleteInboundMapping: (m: any) => void;
    deleteOutboundMapping: (m: any) => void;
    convertToManualInbound: (m: any) => void;
    convertToManualOutbound: (m: any) => void;
    restoreAutoInboundMapping: (m: any) => void;
    restoreAutoOutboundMapping: (m: any) => void;
}

export default function InspectorSidebar({
                                             selectedItemType, selectedSpdhTag, selectedOutField, selectedCtrxPath,
                                             editingParams, editingDesc, isSaving, lineageLinks, outboundLinks,
                                             setEditingDesc, setEditingParams, handleClearStates,
                                             handleSpdhOrOutboundSave, handleCtrxSave, deleteInboundMapping, deleteOutboundMapping,
                                             convertToManualInbound, convertToManualOutbound, restoreAutoInboundMapping, restoreAutoOutboundMapping
                                         }: InspectorSidebarProps) {

    if (!selectedItemType) return null;

    return (
        <div style={{ width: '360px', height: '100%', background: '#ffffff', borderLeft: '1px solid #cbd5e1', display: 'flex', flexDirection: 'column', padding: '20px', boxSizing: 'border-box', overflowY: 'auto', boxShadow: '-4px 0 12px rgba(0,0,0,0.05)', zIndex: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                <span style={{ fontSize: '11px', background: selectedItemType === 'ctrx' ? '#dcfce7' : (selectedItemType === 'spdh' ? '#e0f2fe' : '#e0e7ff'), color: selectedItemType === 'ctrx' ? '#14532d' : (selectedItemType === 'spdh' ? '#0369a1' : '#4338ca'), padding: '4px 8px', borderRadius: '12px', fontWeight: 'bold', fontFamily: 'sans-serif' }}>
                    {selectedItemType === 'ctrx' ? 'CTRX Specification Element' : (selectedItemType === 'spdh' ? 'SPDH Inbound Tag' : 'Outbound BIC ISO Field')}
                </span>
                <button onClick={handleClearStates} style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '16px', color: '#94a3b8' }}>✕</button>
            </div>

            {(selectedItemType === 'spdh' || selectedItemType === 'outbound') && (
                <form onSubmit={handleSpdhOrOutboundSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <h4 style={{ margin: 0, fontFamily: 'monospace', color: '#1e293b' }}>
                        {selectedItemType === 'spdh' ? `[${selectedSpdhTag?.tag}] - ${selectedSpdhTag?.name}` : `${selectedOutField?.name}`}
                    </h4>

                    <div style={{ padding: '12px', background: '#f1f5f9', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#475569', display: 'block', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                            End-To-End Lineage Chain
                        </label>
                        {selectedItemType === 'spdh' ? (
                            (() => {
                                const matched = lineageLinks.filter(l => (l.fieldName || l.fidName || '').toLowerCase() === selectedSpdhTag?.name.toLowerCase());
                                return matched.length > 0 ? (
                                    <div style={{ fontSize: '12px', color: '#1e293b' }}>
                                        {matched.map((m, idx) => {
                                            const outDest = outboundLinks.find(ol => ol.ctrxPath.toLowerCase() === m.ctrxPath.toLowerCase());
                                            return (
                                                <div key={idx} style={{ background: '#fff', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1', marginBottom: '6px', position: 'relative' }}>
                                                    <div style={{ position: 'absolute', top: '6px', right: '6px', display: 'flex', gap: '6px', alignItems: 'center' }}>
                                                        {m.isAuto ? (
                                                            <button type="button" onClick={() => convertToManualInbound(m)} style={{ fontSize: '9px', background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Convert to Manual Override">AUTO</button>
                                                        ) : (
                                                            <>
                                                                {m.hasAuto && <button type="button" onClick={() => restoreAutoInboundMapping(m)} style={{ fontSize: '9px', background: '#dcfce7', color: '#14532d', border: '1px solid #86efac', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Restore Automated Mapping">RESTORE AUTO</button>}
                                                                <button type="button" onClick={() => deleteInboundMapping(m)} style={{ background: 'none', border: 'none', color: '#ef4444', fontWeight: 'bold', cursor: 'pointer', fontSize: '11px' }} title="Delete Mapping">✕</button>
                                                            </>
                                                        )}
                                                    </div>
                                                    <div style={{ fontSize: '10px', color: '#0369a1', fontWeight: 'bold', marginBottom: '2px' }}>➔ CTRX Attribute:</div>
                                                    <div style={{ fontFamily: 'monospace', fontSize: '11px', wordBreak: 'break-all', color: '#334155', marginBottom: '4px', paddingRight: '16px' }}>{m.ctrxPath}</div>
                                                    <div style={{ fontSize: '10px', color: '#4338ca', fontWeight: 'bold', marginBottom: '2px' }}>➔ Downstream Outbound Destination:</div>
                                                    <div style={{ fontFamily: 'monospace', fontSize: '11px', color: outDest ? '#4338ca' : '#64748b', fontStyle: outDest ? 'normal' : 'italic' }}>
                                                        {outDest ? `${outDest.fieldName} [BIC ISO]` : 'Unmapped downstream'}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                ) : (
                                    <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>No active data pipeline mappings defined.</div>
                                );
                            })()
                        ) : (
                            (() => {
                                const matched = outboundLinks.filter(l => (l.fieldName || '').toLowerCase() === selectedOutField?.name.toLowerCase());
                                return matched.length > 0 ? (
                                    <div style={{ fontSize: '12px', color: '#1e293b' }}>
                                        {matched.map((m, idx) => {
                                            const inSrc = lineageLinks.find(il => il.ctrxPath.toLowerCase() === m.ctrxPath.toLowerCase());
                                            const srcDialect = inSrc ? checkInboundDialect(inSrc.fieldName || '') : '';
                                            return (
                                                <div key={idx} style={{ background: '#fff', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1', marginBottom: '6px', position: 'relative' }}>
                                                    <div style={{ position: 'absolute', top: '6px', right: '6px', display: 'flex', gap: '6px', alignItems: 'center' }}>
                                                        {m.isAuto ? (
                                                            <button type="button" onClick={() => convertToManualOutbound(m)} style={{ fontSize: '9px', background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Convert to Manual Override">AUTO</button>
                                                        ) : (
                                                            <>
                                                                {m.hasAuto && <button type="button" onClick={() => restoreAutoOutboundMapping(m)} style={{ fontSize: '9px', background: '#dcfce7', color: '#14532d', border: '1px solid #86efac', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Restore Automated Mapping">RESTORE AUTO</button>}
                                                                <button type="button" onClick={() => deleteOutboundMapping(m)} style={{ background: 'none', border: 'none', color: '#ef4444', fontWeight: 'bold', cursor: 'pointer', fontSize: '11px' }} title="Delete Mapping">✕</button>
                                                            </>
                                                        )}
                                                    </div>
                                                    <div style={{ fontSize: '10px', color: '#047857', fontWeight: 'bold', marginBottom: '2px' }}>🞀 CTRX Attribute Source:</div>
                                                    <div style={{ fontFamily: 'monospace', fontSize: '11px', wordBreak: 'break-all', color: '#334155', marginBottom: '4px', paddingRight: '16px' }}>{m.ctrxPath}</div>
                                                    <div style={{ fontSize: '10px', color: '#0284c7', fontWeight: 'bold', marginBottom: '2px' }}>🞀 Originating Inbound Source:</div>
                                                    <div style={{ fontFamily: 'monospace', fontSize: '11px', color: inSrc ? '#0369a1' : '#64748b', fontStyle: inSrc ? 'normal' : 'italic' }}>
                                                        {inSrc ? `${inSrc.fieldName} [${srcDialect}]` : 'No upstream source connection'}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                ) : (
                                    <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>No active data pipeline mappings defined.</div>
                                );
                            })()
                        )}
                    </div>

                    <div>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Functional Description</label>
                        <textarea rows={6} value={editingDesc} onChange={(e) => setEditingDesc(e.target.value)} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', color: '#1e293b', background: '#fff', fontSize: '13px', lineHeight: '1.4' }} />
                    </div>
                    <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '10px', background: '#0284c7', color: 'white', border: 'none', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }}>
                        {isSaving ? 'Updating...' : '💾 Save Parameter Description'}
                    </button>
                </form>
            )}

            {selectedItemType === 'ctrx' && editingParams && (
                <form onSubmit={handleCtrxSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <h4 style={{ margin: 0, fontFamily: 'monospace', color: '#1e293b' }}>{selectedCtrxPath?.split('/').pop()}</h4>
                    <p style={{ margin: 0, fontSize: '11px', color: '#64748b', wordBreak: 'break-all' }}>{selectedCtrxPath}</p>

                    <div style={{ padding: '12px', background: '#f1f5f9', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#475569', display: 'block', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                            Cross-Protocol Lineage Mappings
                        </label>
                        {(() => {
                            const matchedIn = lineageLinks.filter(l => l.ctrxPath.toLowerCase() === selectedCtrxPath?.toLowerCase());
                            const matchedOut = outboundLinks.filter(l => l.ctrxPath.toLowerCase() === selectedCtrxPath?.toLowerCase());
                            return (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                    <div>
                                        <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '4px' }}>Inbound Source (SPDH):</span>
                                        {matchedIn.length > 0 ? (
                                            matchedIn.map((m, idx) => {
                                                const name = m.fieldName || m.fidName || '';
                                                const dialect = checkInboundDialect(name);
                                                return (
                                                    <div key={idx} style={{ fontSize: '12px', background: '#fff', padding: '6px 8px', borderRadius: '4px', border: '1px solid #cbd5e1', marginBottom: '4px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                                        <span style={{ fontFamily: 'monospace', fontWeight: 'bold', color: '#0369a1' }}>{name} <span style={{ fontSize: '9px', color: '#64748b', fontWeight: 'normal' }}>({dialect})</span></span>
                                                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center'}}>
                                                            {m.isAuto ? (
                                                                <button type="button" onClick={() => convertToManualInbound(m)} style={{ fontSize: '9px', background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Convert to Manual Override">AUTO</button>
                                                            ) : (
                                                                <>
                                                                    {m.hasAuto && <button type="button" onClick={() => restoreAutoInboundMapping(m)} style={{ fontSize: '9px', background: '#dcfce7', color: '#14532d', border: '1px solid #86efac', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Restore Automated Mapping">RESTORE AUTO</button>}
                                                                    <button type="button" onClick={() => deleteInboundMapping(m)} style={{ background: 'none', border: 'none', color: '#ef4444', fontWeight: 'bold', cursor: 'pointer' }} title="Delete Mapping">✕</button>
                                                                </>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })
                                        ) : (
                                            <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>None</div>
                                        )}
                                    </div>

                                    <div>
                                        <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '4px' }}>Outbound Destination (Core Out):</span>
                                        {matchedOut.length > 0 ? (
                                            matchedOut.map((m, idx) => (
                                                <div key={idx} style={{ fontSize: '12px', background: '#fff', padding: '6px 8px', borderRadius: '4px', border: '1px solid #cbd5e1', marginBottom: '4px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                                    <span style={{ fontFamily: 'monospace', fontWeight: 'bold', color: '#4338ca' }}>{m.fieldName} <span style={{ fontSize: '9px', color: '#64748b', fontWeight: 'normal' }}>(ISO)</span></span>
                                                    <div style={{ display: 'flex', gap: '6px', alignItems: 'center'}}>
                                                        {m.isAuto ? (
                                                            <button type="button" onClick={() => convertToManualOutbound(m)} style={{ fontSize: '9px', background: '#e0e7ff', color: '#4338ca', border: '1px solid #c7d2fe', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Convert to Manual Override">AUTO</button>
                                                        ) : (
                                                            <>
                                                                {m.hasAuto && <button type="button" onClick={() => restoreAutoOutboundMapping(m)} style={{ fontSize: '9px', background: '#dcfce7', color: '#14532d', border: '1px solid #86efac', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }} title="Restore Automated Mapping">RESTORE AUTO</button>}
                                                                <button type="button" onClick={() => deleteOutboundMapping(m)} style={{ background: 'none', border: 'none', color: '#ef4444', fontWeight: 'bold', cursor: 'pointer' }} title="Delete Mapping">✕</button>
                                                            </>
                                                        )}
                                                    </div>
                                                </div>
                                            ))
                                        ) : (
                                            <div style={{ fontSize: '12px', color: '#64748b', fontStyle: 'italic' }}>None</div>
                                        )}
                                    </div>
                                </div>
                            );
                        })()}
                    </div>

                    <div><label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Database Data Type</label>
                        <select value={editingParams.dataType} onChange={(e) => setEditingParams({ ...editingParams, dataType: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', background: '#fff', color: '#1e293b' }}>
                            <option value="VARCHAR">VARCHAR</option><option value="NUMERIC">NUMERIC</option><option value="BIGINT">BIGINT</option><option value="INT">INT</option><option value="TIMESTAMP">TIMESTAMP</option><option value="CHAR">CHAR</option>
                        </select>
                    </div>
                    <div><label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Length</label>
                        <input type="text" value={editingParams.length} onChange={(e) => setEditingParams({ ...editingParams, length: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', color: '#1e293b', background: '#fff' }} />
                    </div>
                    <div><label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Database Table</label>
                        <input type="text" value={editingParams.dbTable} onChange={(e) => setEditingParams({ ...editingParams, dbTable: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', color: '#1e293b', background: '#fff' }} />
                    </div>
                    <div><label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Example Value</label>
                        <input type="text" readOnly value={editingParams.exampleValue} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', color: '#64748b', background: '#f8fafc' }} />
                    </div>
                    <div><label style={{ fontSize: '11px', fontWeight: 'bold', display: 'block', marginBottom: '4px', color: '#475569' }}>Functional Description</label>
                        <textarea rows={4} value={editingParams.description} onChange={(e) => setEditingParams({ ...editingParams, description: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', color: '#1e293b', background: '#fff' }} />
                    </div>
                    <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '10px', background: '#10b981', color: 'white', border: 'none', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }}>
                        {isSaving ? 'Saving...' : '💾 Save Parameter Config'}
                    </button>
                </form>
            )}
        </div>
    );
}