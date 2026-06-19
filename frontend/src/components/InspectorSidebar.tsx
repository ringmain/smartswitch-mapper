import { getDialectColor } from '../hooks/useMappingEngine';

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
                                             spdhTagsList,
                                             outFieldsList,
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

    if (!selectedItemType) return null;

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

    const requestInbound = relevantInbound.filter(l => l.type !== 'RESPONSE');
    const responseInbound = relevantInbound.filter(l => l.type === 'RESPONSE');

    const requestOutbound = relevantOutbound.filter(l => l.type !== 'RESPONSE');
    const responseOutbound = relevantOutbound.filter(l => l.type === 'RESPONSE');

    const renderMappingItem = (link: any, isInbound: boolean) => {
        const tagObj = isInbound
            ? spdhTagsList?.find((t: any) => t.name === link.fieldName)
            : outFieldsList?.find((t: any) => t.name === link.fieldName);

        const tagStr = tagObj?.tag ? `[${tagObj.tag}] ` : '';

        let dialectBadge = null;
        if (isInbound && tagObj?.dialect) {
            const shortDialect = tagObj.dialect.replace('SpdhTag', '').replace('V1', '');
            const pillColors = getDialectColor(tagObj.dialect);
            dialectBadge = (
                <span style={{
                    background: pillColors.bg,
                    color: pillColors.text,
                    padding: '2px 5px',
                    borderRadius: '4px',
                    fontSize: '9px',
                    fontWeight: 'bold',
                    textTransform: 'uppercase',
                    border: `1px solid ${pillColors.border}`,
                    marginRight: '6px',
                    display: 'inline-block',
                }}>
                    {shortDialect}
                </span>
            );
        }

        return (
            <div key={`${link.ctrxPath}-${link.fieldName}-${link.type}`} style={{ backgroundColor: '#f8fafc', padding: '10px', borderRadius: '4px', border: '1px solid #e2e8f0', marginBottom: '8px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontWeight: 'bold', color: '#334155', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center' }}>
                        {dialectBadge}
                        <span>{isInbound ? `${tagStr}${link.fieldName} ➔ CTRX` : `CTRX ➔ ${tagStr}${link.fieldName}`}</span>
                    </div>
                    <span style={{ fontSize: '9px', padding: '2px 4px', borderRadius: '4px', backgroundColor: link.isAuto ? '#dbeafe' : '#fef3c7', color: link.isAuto ? '#1e40af' : '#9a3412', height: 'fit-content', marginLeft: '6px' }}>
                        {link.isAuto ? 'AUTO' : 'MANUAL'}
                    </span>
                </div>

                <div style={{ display: 'flex', gap: '6px', marginTop: '6px' }}>
                    <button
                        onClick={() => isInbound ? deleteInboundMapping(link) : deleteOutboundMapping(link)}
                        style={{ flex: 1, padding: '4px', fontSize: '10px', color: '#ef4444', backgroundColor: '#fee2e2', border: 'none', borderRadius: '3px', cursor: 'pointer' }}>
                        Delete
                    </button>

                    {link.isAuto && (
                        <button
                            onClick={() => isInbound ? convertToManualInbound(link) : convertToManualOutbound(link)}
                            style={{ flex: 1, padding: '4px', fontSize: '10px', color: '#10b981', backgroundColor: '#d1fae5', border: 'none', borderRadius: '3px', cursor: 'pointer' }}>
                            Override
                        </button>
                    )}

                    {!link.isAuto && link.hasAuto && (
                        <button
                            onClick={() => isInbound ? restoreAutoInboundMapping(link) : restoreAutoOutboundMapping(link)}
                            style={{ flex: 1, padding: '4px', fontSize: '10px', color: '#3b82f6', backgroundColor: '#dbeafe', border: 'none', borderRadius: '3px', cursor: 'pointer' }}>
                            Restore Auto
                        </button>
                    )}
                </div>
            </div>
        );
    };

    return (
        <aside style={{ width: '340px', height: '100%', backgroundColor: '#ffffff', borderLeft: '1px solid #cbd5e1', display: 'flex', flexDirection: 'column', boxShadow: '-4px 0 15px rgba(0,0,0,0.05)', zIndex: 50, flexShrink: 0 }}>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#f1f5f9', flexShrink: 0 }}>
                <h3 style={{ margin: 0, fontSize: '13px', color: '#0f172a', textTransform: 'uppercase', fontWeight: 'bold' }}>Inspector</h3>
                <button onClick={handleClearStates} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: '#64748b', padding: 0 }}>&times;</button>
            </div>

            <div style={{ flex: '1 1 0', overflowY: 'auto', overflowX: 'hidden', padding: '16px' }}>

                <div style={{ marginBottom: '20px' }}>
                    <h4 style={{ margin: '0 0 8px 0', fontSize: '12px', color: '#475569' }}>
                        {selectedItemType === 'spdh' && 'Inbound Tag Details'}
                        {selectedItemType === 'outbound' && 'Outbound Field Details'}
                        {selectedItemType === 'ctrx' && 'CTRX Node Details'}
                    </h4>

                    <div style={{ fontSize: '13px', fontWeight: 'bold', color: '#0f172a', wordBreak: 'break-all', marginBottom: '12px' }}>
                        {selectedItemType === 'spdh' && selectedSpdhTag && `[${selectedSpdhTag.tag}] ${selectedSpdhTag.name}`}
                        {selectedItemType === 'outbound' && selectedOutField?.name}
                        {selectedItemType === 'ctrx' && selectedCtrxPath}
                    </div>

                    {(selectedItemType === 'spdh' || selectedItemType === 'outbound') && (
                        <form onSubmit={handleSpdhOrOutboundSave}>
                            <label style={{ display: 'block', fontSize: '11px', color: '#64748b', marginBottom: '4px' }}>Description</label>
                            <textarea
                                value={editingDesc}
                                onChange={(e) => setEditingDesc(e.target.value)}
                                rows={4}
                                style={{ width: '100%', padding: '6px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '8px', boxSizing: 'border-box', fontFamily: 'inherit', fontSize: '12px' }}
                            />
                            <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '6px', backgroundColor: '#0ea5e9', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '12px' }}>
                                {isSaving ? 'Saving...' : 'Save Description'}
                            </button>
                        </form>
                    )}

                    {selectedItemType === 'ctrx' && editingParams && (
                        <form onSubmit={handleCtrxSave}>
                            {editingParams.dbTable && (
                                <div style={{ marginBottom: '10px' }}>
                                    <label style={{ display: 'block', fontSize: '11px', color: '#64748b', marginBottom: '4px' }}>Parent DB Table</label>
                                    <div style={{ display: 'inline-block', padding: '2px 5px', backgroundColor: '#f1f5f9', color: '#475569', borderRadius: '4px', border: '1px solid #cbd5e1', fontSize: '9px', fontWeight: 'bold', fontFamily: 'monospace', textTransform: 'uppercase' }}>
                                        🗄️ {editingParams.dbTable}
                                    </div>
                                </div>
                            )}

                            <label style={{ display: 'block', fontSize: '11px', color: '#64748b', marginBottom: '2px' }}>Data Type</label>
                            <input type="text" value={editingParams.dataType || ''} onChange={(e) => setEditingParams({ ...editingParams, dataType: e.target.value })} style={{ width: '100%', padding: '6px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '8px', boxSizing: 'border-box', fontSize: '12px' }} />

                            <label style={{ display: 'block', fontSize: '11px', color: '#64748b', marginBottom: '2px' }}>Length</label>
                            <input type="text" value={editingParams.length || ''} onChange={(e) => setEditingParams({ ...editingParams, length: e.target.value })} style={{ width: '100%', padding: '6px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '8px', boxSizing: 'border-box', fontSize: '12px' }} />

                            <label style={{ display: 'block', fontSize: '11px', color: '#64748b', marginBottom: '2px' }}>Description</label>
                            <textarea value={editingParams.description || ''} onChange={(e) => setEditingParams({ ...editingParams, description: e.target.value })} rows={3} style={{ width: '100%', padding: '6px', border: '1px solid #cbd5e1', borderRadius: '4px', marginBottom: '8px', boxSizing: 'border-box', fontFamily: 'inherit', fontSize: '12px' }} />

                            <button type="submit" disabled={isSaving} style={{ width: '100%', padding: '6px', backgroundColor: '#0ea5e9', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '12px' }}>
                                {isSaving ? 'Saving...' : 'Save Meta Params'}
                            </button>
                        </form>
                    )}
                </div>

                <hr style={{ border: 'none', borderTop: '1px solid #e2e8f0', margin: '16px 0' }} />

                <div>
                    <h3 style={{ margin: '0 0 12px 0', fontSize: '13px', color: '#0f172a' }}>Active Connections</h3>

                    {(requestInbound.length > 0 || requestOutbound.length > 0) && (
                        <div style={{ marginBottom: '20px' }}>
                            <h4 style={{ color: '#22c55e', textTransform: 'uppercase', fontSize: '10px', letterSpacing: '0.5px', borderBottom: '1px solid #bbf7d0', paddingBottom: '4px', marginBottom: '8px' }}>
                                Request Mappings
                            </h4>
                            {requestInbound.map(link => renderMappingItem(link, true))}
                            {requestOutbound.map(link => renderMappingItem(link, false))}
                        </div>
                    )}

                    {(responseInbound.length > 0 || responseOutbound.length > 0) && (
                        <div style={{ marginBottom: '20px' }}>
                            <h4 style={{ color: '#ef4444', textTransform: 'uppercase', fontSize: '10px', letterSpacing: '0.5px', borderBottom: '1px solid #fecaca', paddingBottom: '4px', marginBottom: '8px' }}>
                                Response Mappings
                            </h4>
                            {responseInbound.map(link => renderMappingItem(link, true))}
                            {responseOutbound.map(link => renderMappingItem(link, false))}
                        </div>
                    )}

                    {requestInbound.length === 0 && requestOutbound.length === 0 && responseInbound.length === 0 && responseOutbound.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '16px 0', color: '#94a3b8', fontSize: '12px' }}>
                            No active connections found for this node.
                        </div>
                    )}
                </div>
            </div>
        </aside>
    );
}