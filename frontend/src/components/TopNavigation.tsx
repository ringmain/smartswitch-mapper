import { getDialectColor } from '../hooks/useMappingEngine';

interface TopNavigationProps {
    cNodeW: number;
    searchQuery: string;
    setSearchQuery: (val: string) => void;
    activeDialects: string[];
    setActiveDialects: (val: string[]) => void;
    inChannelDialect: string;
    setInChannelDialect: (val: string) => void;
    outChannelDialect: string;
    setOutChannelDialect: (val: string) => void;
    handleExpandAll: () => void;
    handleClearStates: () => void;
    setExpandedPaths: (paths: Set<string>) => void;
    hideUnmapped: boolean;
    setHideUnmapped: (val: boolean) => void;
    hasHiddenAutos: boolean;
    handleRestoreHiddenAutos: () => void;
}

const AVAILABLE_SPDH_DIALECTS = [
    "BaseSpdhTag", "FestV1SpdhTag", "MosV1SpdhTag", "NuszV1SpdhTag",
    "PosMolV1SpdhTag", "QrPaymentV1SpdhTag", "SShopV1SpdhTag",
    "TopUpMobileSpdhTag", "UpcV1SpdhTag"
];

export default function TopNavigation({
                                          cNodeW, searchQuery, setSearchQuery, activeDialects, setActiveDialects,
                                          inChannelDialect, setInChannelDialect, outChannelDialect, setOutChannelDialect,
                                          handleExpandAll, handleClearStates, setExpandedPaths, hideUnmapped, setHideUnmapped,
                                          hasHiddenAutos, handleRestoreHiddenAutos
                                      }: TopNavigationProps) {
    return (
        <>
            <div style={{ background: '#0f172a', padding: '16px 24px 4px 24px', borderBottom: '1px solid #1e293b', zIndex: 11, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h1 style={{ fontSize: '24px', fontWeight: 'bold', margin: 0, color: '#10b981', fontFamily: 'sans-serif', letterSpacing: '-0.3px' }}>Smartswitch Attribute Mapper</h1>
                <div style={{ display: 'flex', gap: '6px' }}>
                    {hasHiddenAutos && <button onClick={handleRestoreHiddenAutos} style={{ padding: '6px 14px', background: '#b45309', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '11px', cursor: 'pointer', fontWeight: 'bold' }}>Restore All Autos</button>}
                    <button onClick={() => setHideUnmapped(!hideUnmapped)} style={{ padding: '6px 14px', background: hideUnmapped ? '#6366f1' : '#334155', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '11px', cursor: 'pointer', fontWeight: 'bold' }}>{hideUnmapped ? 'Show All Elements' : 'Hide Unmapped'}</button>
                    <button onClick={handleExpandAll} style={{ padding: '6px 14px', background: '#059669', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '11px', cursor: 'pointer', fontWeight: 'bold' }}>Expand All</button>
                    <button onClick={() => { setExpandedPaths(new Set()); handleClearStates(); }} style={{ padding: '6px 14px', background: '#475569', border: 'none', borderRadius: '4px', color: '#fff', fontSize: '11px', cursor: 'pointer', fontWeight: '600' }}>Collapse All</button>
                </div>
            </div>

            <div style={{ display: 'flex', width: '100%', height: '52px', background: '#1e293b', zIndex: 10, boxSizing: 'border-box', borderBottom: '1px solid #334155', alignItems: 'center', position: 'relative' }}>
                <div style={{ position: 'absolute', right: 'calc(50% + 202px)', display: 'flex', alignItems: 'center', gap: '16px', boxSizing: 'border-box' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#94a3b8', textTransform: 'uppercase', fontFamily: 'sans-serif' }}>In Channel Dialect:</label>
                        <select value={inChannelDialect} onChange={(e) => { setInChannelDialect(e.target.value); handleClearStates(); }} style={{ padding: '5px 10px', background: '#0f172a', color: '#fff', border: '1px solid #475569', borderRadius: '4px', fontSize: '12px', width: '165px', cursor: 'pointer' }}>
                            <option value="NONE">NONE</option>
                            <option value="GENERIC">GENERIC (Manual)</option>
                            <option value="AUTO_SPDH_GENERIC">AUTO_SPDH_GENERIC</option>
                        </select>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#94a3b8', textTransform: 'uppercase', fontFamily: 'sans-serif', whiteSpace: 'nowrap' }}>SPDH Dialects:</label>
                        <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                            {activeDialects.map(d => {
                                const shortName = d.replace('SpdhTag', '').replace('V1', '');
                                const pillColors = getDialectColor(d);
                                return (
                                    <div key={d} style={{ background: pillColors.bg, color: pillColors.text, border: `1px solid ${pillColors.border}`, padding: '2px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                        {shortName}
                                        <button onClick={() => { handleClearStates(); setActiveDialects(activeDialects.filter(x => x !== d)); }} style={{ background: 'none', border: 'none', color: pillColors.text, cursor: 'pointer', padding: 0, fontSize: '10px', fontWeight: 'bold' }}>✕</button>
                                    </div>
                                );
                            })}
                            <select value="" onChange={(e) => {
                                if (e.target.value && !activeDialects.includes(e.target.value)) {
                                    handleClearStates();
                                    setActiveDialects([...activeDialects, e.target.value]);
                                }
                            }} style={{ padding: '4px 6px', background: '#0f172a', color: '#fff', border: '1px solid #475569', borderRadius: '4px', fontSize: '10px', cursor: 'pointer' }}>
                                <option value="">+ Add</option>
                                {AVAILABLE_SPDH_DIALECTS.filter(d => !activeDialects.includes(d)).map(d => (
                                    <option key={d} value={d}>{d}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                </div>

                <div style={{ position: 'absolute', left: '50%', transform: 'translateX(-50%)', width: `${cNodeW}px`, boxSizing: 'border-box' }}>
                    <div style={{ position: 'relative', width: '100%' }}>
                        <input type="text" placeholder="🔍 Search inbound tags, ctrx nodes, or outbound fields..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} style={{ width: '100%', padding: '6px 28px 6px 14px', background: '#0f172a', border: '1px solid #475569', borderRadius: '6px', color: '#fff', fontSize: '12px', boxSizing: 'border-box' }} />
                        {searchQuery && (
                            <button onClick={() => setSearchQuery('')} style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' }}>✕</button>
                        )}
                    </div>
                </div>

                <div style={{ position: 'absolute', left: 'calc(50% + 202px)', display: 'flex', alignItems: 'center', gap: '16px', boxSizing: 'border-box' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#94a3b8', textTransform: 'uppercase', fontFamily: 'sans-serif', whiteSpace: 'nowrap' }}>Out Channel Protocol:</label>
                        <select value="BIC_ISO" disabled style={{ padding: '5px 10px', background: '#0f172a', color: '#fff', border: '1px solid #475569', borderRadius: '4px', fontSize: '12px', width: '90px', cursor: 'not-allowed' }}>
                            <option value="BIC_ISO">BIC ISO</option>
                        </select>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <label style={{ fontSize: '11px', fontWeight: 'bold', color: '#94a3b8', textTransform: 'uppercase', fontFamily: 'sans-serif', whiteSpace: 'nowrap' }}>Out Channel Dialect:</label>
                        <select value={outChannelDialect} onChange={(e) => { setOutChannelDialect(e.target.value); handleClearStates(); }} style={{ padding: '5px 10px', background: '#0f172a', color: '#fff', border: '1px solid #475569', borderRadius: '4px', fontSize: '12px', width: '175px', cursor: 'pointer' }}>
                            <option value="NONE">NONE</option>
                            <option value="GENERIC">GENERIC (Manual)</option>
                            <option value="AUTO_BIC_ISO_GENERIC">AUTO_BIC_ISO_GENERIC</option>
                        </select>
                    </div>
                </div>
            </div>
        </>
    );
}