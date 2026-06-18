export default function InspectorSidebar({
                                             selectedItemType,
                                             selectedSpdhTag,
                                             selectedOutField,
                                             selectedCtrxPath,
                                             editingParams,
                                             editingDesc,
                                             isSaving,
                                             lineageLinks,
                                             outboundLinks,
                                             setEditingDesc,
                                             setEditingParams,
                                             handleClearStates,
                                             handleSpdhOrOutboundSave,
                                             handleCtrxSave,
                                             deleteInboundMapping,
                                             deleteOutboundMapping,
                                             convertToManualInbound,
                                             convertToManualOutbound,
                                             restoreAutoInboundMapping,
                                             restoreAutoOutboundMapping
                                         }: any) {

    // If nothing is selected, don't render the sidebar
    if (!selectedItemType) return null;

    // 1. Filter links relevant to the currently selected node
    let relevantInbound: any[] = [];
    let relevantOutbound: any[] = [];

    if (selectedItemType === 'spdh' && selectedSpdhTag) {
        relevantInbound = lineageLinks.filter((l: any) => l.fieldName === selectedSpdhTag.name);
    } else if (selectedItemType === 'outbound' && selectedOutField) {
        relevantOutbound = outboundLinks.filter((l: any) => l.fieldName === selectedOutField.name);
    } else if (selectedItemType === 'ctrx' && selectedCtrxPath) {
        relevantInbound = lineageLinks.filter((l: any) => l.ctrxPath === selectedCtrxPath);
        relevantOutbound = outboundLinks.filter((l: any) => l.ctrxPath === selectedCtrxPath);
    }

    // 2. Partition the relevant links into REQUEST and RESPONSE categories
    const requestInbound = relevantInbound.filter(l => l.type !== 'RESPONSE');
    const responseInbound = relevantInbound.filter(l => l.type === 'RESPONSE');

    const requestOutbound = relevantOutbound.filter(l => l.type !== 'RESPONSE');
    const responseOutbound = relevantOutbound.filter(l => l.type === 'RESPONSE');

    // Helper function to render individual mapping items
    const renderMappingItem = (link: any, isInbound: boolean) => (
        <div key={`${link.ctrxPath}-${link.fieldName}-${link.type}`} style={{ backgroundColor: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid #e2e8f0', marginBottom: '8px', fontSize: '12px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontWeight: 'bold', color: '#334155' }}>
                <span>{isInbound ? `${link.fieldName} ➔ CTRX` : `CTRX ➔ ${link.fieldName}`}</span>
                <span style={{ fontSize: '10px', padding: '2px 6px', borderRadius: '4px', backgroundColor: link.isAuto ? '#dbeafe' : '#fef3c7', color: link.isAuto ? '#1e40af' : '#9a3412' }}>
                    {link.isAuto ? 'AUTO' : 'MANUAL'}
                </span>
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                <button
                    onClick={() => isInbound ? deleteInboundMapping(link) : deleteOutboundMapping(link)}
                    style={{ flex: 1, padding: '4px', fontSize: '11px', color: '#ef4444', backgroundColor: '#fee2e2', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
                    Delete
                </button>

                {link.isAuto && (
                    <button
                        onClick={() => isInbound ? convertToManualInbound(link) : convertToManualOutbound(link)}
                        style={{ flex: 1, padding: '4px', fontSize: '11px', color: '#10b981', backgroundColor: '#d1fae5', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
                        Override
                    </button>
                )}

                {!link.isAuto && link.hasAuto && (
                    <button
                        onClick={() => isInbound ? restoreAutoInboundMapping(link) : restoreAutoOutboundMapping(link)}
                        style={{ flex: 1, padding: '4px', fontSize: '11px', color: '#3b82f6', backgroundColor: '#dbeafe', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
                        Restore Auto
                    </button>
                )}
            </div>
        </div>
    );

    return (
        <aside style={{ width: '360px', height: '100%', backgroundColor: '#ffffff', borderLeft: '1px solid #cbd5e1', display: 'flex', flexDirection: 'column', boxShadow: '-4px 0 15px rgba(0,0,0,0.05)', zIndex: 50 }}>
            {/* Sidebar Header */}
            <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#f1f5f9' }}>
                <h3 style={{ margin: 0, fontSize: '14px', color: '#0f172a', textTransform: 'uppercase' }}>Inspector</h3>
                <button onClick={handleClearStates} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: '#64748b' }}>&times;</button>
            </div>

            {/* Sidebar Body */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>

                {/* Node Details Section */}
                <div style={{ marginBottom: '24px' }}>
                    <h4 style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#475569' }}>
                        {selectedItemType === 'spdh' && 'Inbound Tag Details'}
                        {selectedItemType === 'outbound' && 'Outbound Field Details'}
                        {selectedItemType === 'ctrx' && 'CTRX Node Details'}
                    </h4>

                    <div style={{ fontSize: '14px', fontWeight: 'bold', color: '#0f172a', wordBreak: 'break-all', marginBottom: '16px' }}>
                        {selectedItemType === 'spdh' && selectedSpdhTag?.name}
                        {selectedItemType === 'outbound' && selectedOutField?.name}
                        {selectedItemType === 'ctrx' && selectedCtrxPath}
                    </div>

                    {/* Forms */}
                    {(selectedItemType === 'spdh' || selectedItemType === 'outbound') && (
                        <form onSubmit={handleSpdhOrOutboundSave}>
                            <label style={{ display: 'block', fontSize: '12px', color: '#64748b', marginBottom: '4px' }}>Description</label>
                            <textarea
                                value={editingDesc}
                                onChange={(e) => setEditingDesc(e.target.value)}
                                rows={4}
                                style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '12px', boxSizing: 'border-box', fontFamily: 'inherit' }}
                            />
                            <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '8px', backgroundColor: '#0ea5e9', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                                {isSaving ? 'Saving...' : 'Save Description'}
                            </button>
                        </form>
                    )}

                    {selectedItemType === 'ctrx' && editingParams && (
                        <form onSubmit={handleCtrxSave}>
                            <label style={{ display: 'block', fontSize: '12px', color: '#64748b', marginBottom: '4px' }}>Data Type</label>
                            <input type="text" value={editingParams.dataType || ''} onChange={(e) => setEditingParams({ ...editingParams, dataType: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '12px', boxSizing: 'border-box' }} />

                            <label style={{ display: 'block', fontSize: '12px', color: '#64748b', marginBottom: '4px' }}>Length</label>
                            <input type="text" value={editingParams.length || ''} onChange={(e) => setEditingParams({ ...editingParams, length: e.target.value })} style={{ width: '100%', padding: '8px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '12px', boxSizing: 'border-box' }} />

                            <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '8px', backgroundColor: '#0ea5e9', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                                {isSaving ? 'Saving...' : 'Save Meta Params'}
                            </button>
                        </form>
                    )}
                </div>

                <hr style={{ border: 'none', borderTop: '1px solid #e2e8f0', margin: '24px 0' }} />

                {/* MAPPINGS SECTION */}
                <div>
                    <h3 style={{ margin: '0 0 16px 0', fontSize: '14px', color: '#0f172a' }}>Active Connections</h3>

                    {/* --- REQUEST MAPPINGS BLOCK --- */}
                    {(requestInbound.length > 0 || requestOutbound.length > 0) && (
                        <div style={{ marginBottom: '24px' }}>
                            <h4 style={{ color: '#22c55e', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.5px', borderBottom: '1px solid #bbf7d0', paddingBottom: '4px', marginBottom: '12px' }}>
                                Request Mappings
                            </h4>
                            {requestInbound.map(link => renderMappingItem(link, true))}
                            {requestOutbound.map(link => renderMappingItem(link, false))}
                        </div>
                    )}

                    {/* --- RESPONSE MAPPINGS BLOCK --- */}
                    {(responseInbound.length > 0 || responseOutbound.length > 0) && (
                        <div style={{ marginBottom: '24px' }}>
                            <h4 style={{ color: '#ef4444', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.5px', borderBottom: '1px solid #fecaca', paddingBottom: '4px', marginBottom: '12px' }}>
                                Response Mappings
                            </h4>
                            {responseInbound.map(link => renderMappingItem(link, true))}
                            {responseOutbound.map(link => renderMappingItem(link, false))}
                        </div>
                    )}

                    {/* Empty State */}
                    {requestInbound.length === 0 && requestOutbound.length === 0 && responseInbound.length === 0 && responseOutbound.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '24px 0', color: '#94a3b8', fontSize: '13px' }}>
                            No active connections found for this node.
                        </div>
                    )}
                </div>

            </div>
        </aside>
    );
}