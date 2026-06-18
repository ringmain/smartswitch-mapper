import { useEffect, useState, useMemo } from 'react';
import { ReactFlow, Background, Controls, BaseEdge, ConnectionMode } from '@xyflow/react';
import type { EdgeProps, Connection } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { apiClient } from './api/apiClient';
import { useMappingEngine, customNodeTypes, cNodeW } from './hooks/useMappingEngine';
import type { LeafParams, CommonElement } from './hooks/useMappingEngine';

import TopNavigation from './components/TopNavigation';
import InspectorSidebar from './components/InspectorSidebar';

const RoutedLineageEdge = ({ id, sourceX, sourceY, targetX, targetY, sourceHandleId, data, className, style }: EdgeProps & { className?: string }) => {
    if (typeof sourceX !== 'number' || Number.isNaN(sourceX) || typeof sourceY !== 'number' || Number.isNaN(sourceY) || typeof targetX !== 'number' || Number.isNaN(targetX) || typeof targetY !== 'number' || Number.isNaN(targetY)) return null;

    const isBot = sourceHandleId?.includes('bot');
    const routeX = (data as any)?.routeX || (isBot ? sourceX - 45 : sourceX + 45);

    const path = `M ${sourceX} ${sourceY} L ${routeX} ${sourceY} L ${routeX} ${targetY} L ${targetX} ${targetY}`;
    return <BaseEdge id={id} path={path} style={style} markerEnd={undefined} className={className} />;
};
const edgeTypes = { routed: RoutedLineageEdge };

const validateConnection = (connection: Connection) => {
    const isTop = connection.sourceHandle?.includes('top') && connection.targetHandle?.includes('top');
    const isBot = connection.sourceHandle?.includes('bot') && connection.targetHandle?.includes('bot');
    return Boolean(isTop || isBot);
};

export default function App() {
    const [schemaData, setSchemaData] = useState<any>(null);
    const [spdhTagsList, setSpdhTagsList] = useState<CommonElement[]>([]);
    const [outFieldsList, setOutFieldsList] = useState<CommonElement[]>([]);
    const [lineageLinks, setLineageLinks] = useState<Array<any>>([]);
    const [outboundLinks, setOutboundLinks] = useState<Array<any>>([]);
    const [loading, setLoading] = useState(true);

    const [expandedPaths, setExpandedPaths] = useState<Set<string>>(new Set());
    const [searchQuery, setSearchQuery] = useState('');
    const [activeDialects, setActiveDialects] = useState<string[]>(['BaseSpdhTag']);
    const [inChannelDialect, setInChannelDialect] = useState<string>('NONE');
    const [outChannelDialect, setOutChannelDialect] = useState<string>('NONE');
    const [hideUnmapped, setHideUnmapped] = useState<boolean>(false);
    const [windowWidth, setWindowWidth] = useState(window.innerWidth);

    const [selectedItemType, setSelectedItemType] = useState<'ctrx' | 'spdh' | 'outbound' | null>(null);
    const [selectedCtrxPath, setSelectedCtrxPath] = useState<string | null>(null);
    const [selectedSpdhTag, setSelectedSpdhTag] = useState<CommonElement | null>(null);
    const [selectedOutField, setSelectedOutField] = useState<CommonElement | null>(null);
    const [editingParams, setEditingParams] = useState<LeafParams | null>(null);
    const [editingDesc, setEditingDesc] = useState<string>('');
    const [isSaving, setIsSaving] = useState(false);

    const [deletedInbound, setDeletedInbound] = useState<string[]>(() => JSON.parse(localStorage.getItem('deletedInbound') || '[]'));
    const [deletedOutbound, setDeletedOutbound] = useState<string[]>(() => JSON.parse(localStorage.getItem('deletedOutbound') || '[]'));

    const [localTypes, setLocalTypes] = useState<Record<string, boolean>>(() => JSON.parse(localStorage.getItem('localTypes') || '{}'));
    const getTypeKey = (fieldName: string, ctrxPath: string, dialect: string, type: string) => `${String(fieldName).trim()}|${String(ctrxPath).trim()}|${String(dialect).trim()}|${String(type)}`.toLowerCase();

    const activeLineageLinks = useMemo(() => {
        const map = new Map<string, any>();

        lineageLinks.forEach(l => {
            let derivedType = l.type;
            if (!derivedType) {
                const resKey = getTypeKey(l.fieldName || l.fidName || '', l.ctrxPath || '', l.channelDialect || '', 'RESPONSE');
                derivedType = localTypes[resKey] ? 'RESPONSE' : 'REQUEST';
            }
            const mapKey = getTypeKey(l.fieldName || l.fidName || '', l.ctrxPath || '', l.channelDialect || '', derivedType);

            if (l.isAuto === true || l.isAuto === 'true') {
                map.set(mapKey, { ...l, isAuto: true, hasAuto: true, type: derivedType });
            }
        });

        lineageLinks.forEach(l => {
            if (l.isAuto !== true && l.isAuto !== 'true') {
                let derivedType = l.type;
                if (!derivedType) {
                    const resKey = getTypeKey(l.fieldName || l.fidName || '', l.ctrxPath || '', l.channelDialect || '', 'RESPONSE');
                    derivedType = localTypes[resKey] ? 'RESPONSE' : 'REQUEST';
                }
                const mapKey = getTypeKey(l.fieldName || l.fidName || '', l.ctrxPath || '', l.channelDialect || '', derivedType);
                map.set(mapKey, { ...l, isAuto: false, hasAuto: map.has(mapKey), type: derivedType });
            }
        });

        return Array.from(map.values()).filter(l => {
            const mapKey = getTypeKey(l.fieldName || l.fidName || '', l.ctrxPath || '', l.channelDialect || '', l.type);
            return !(l.isAuto && deletedInbound.includes(mapKey));
        });
    }, [lineageLinks, deletedInbound, localTypes]);

    const activeOutboundLinks = useMemo(() => {
        const map = new Map<string, any>();

        outboundLinks.forEach(l => {
            let derivedType = l.type;
            if (!derivedType) {
                const resKey = getTypeKey(l.fieldName || '', l.ctrxPath || '', l.channelDialect || '', 'RESPONSE');
                derivedType = localTypes[resKey] ? 'RESPONSE' : 'REQUEST';
            }
            const mapKey = getTypeKey(l.fieldName || '', l.ctrxPath || '', l.channelDialect || '', derivedType);

            if (l.isAuto === true || l.isAuto === 'true') {
                map.set(mapKey, { ...l, isAuto: true, hasAuto: true, type: derivedType });
            }
        });

        outboundLinks.forEach(l => {
            if (l.isAuto !== true && l.isAuto !== 'true') {
                let derivedType = l.type;
                if (!derivedType) {
                    const resKey = getTypeKey(l.fieldName || '', l.ctrxPath || '', l.channelDialect || '', 'RESPONSE');
                    derivedType = localTypes[resKey] ? 'RESPONSE' : 'REQUEST';
                }
                const mapKey = getTypeKey(l.fieldName || '', l.ctrxPath || '', l.channelDialect || '', derivedType);
                map.set(mapKey, { ...l, isAuto: false, hasAuto: map.has(mapKey), type: derivedType });
            }
        });

        return Array.from(map.values()).filter(l => {
            const mapKey = getTypeKey(l.fieldName || '', l.ctrxPath || '', l.channelDialect || '', l.type);
            return !(l.isAuto && deletedOutbound.includes(mapKey));
        });
    }, [outboundLinks, deletedOutbound, localTypes]);

    const hasOverrides = activeLineageLinks.some(l => l.hasAuto && !l.isAuto) || activeOutboundLinks.some(l => l.hasAuto && !l.isAuto);
    const hasHiddenAutos = deletedInbound.length > 0 || deletedOutbound.length > 0 || hasOverrides;

    const activeViewportSpaceX = windowWidth - (selectedItemType ? 360 : 0);

    const { nodes, edges } = useMappingEngine({
        schemaData, spdhTagsList, outFieldsList, expandedPaths, searchQuery,
        selectedItemType, selectedSpdhTag, selectedCtrxPath, selectedOutField,
        activeViewportSpaceX, inChannelDialect, outChannelDialect,
        lineageLinks: activeLineageLinks, outboundLinks: activeOutboundLinks, hideUnmapped
    });

    const loadData = () => {
        apiClient.loadWorkspaceData(activeDialects, inChannelDialect, outChannelDialect).then(data => {
            setSchemaData(data.schema);
            setSpdhTagsList(Array.isArray(data.tags) ? data.tags : []);
            setOutFieldsList(Array.isArray(data.outFields) ? data.outFields : []);
            setLineageLinks(Array.isArray(data.transLinks) ? data.transLinks.map(l => ({...l, isAuto: l.isAuto ?? l.auto})) : []);
            setOutboundLinks(Array.isArray(data.outLinks) ? data.outLinks.map(l => ({...l, isAuto: l.isAuto ?? l.auto})) : []);
            setLoading(false);
        });
    };

    useEffect(() => {
        loadData();
        const handleResize = () => setWindowWidth(window.innerWidth);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [activeDialects, inChannelDialect, outChannelDialect]);

    const handleExpandAll = () => {
        if (!schemaData) return;
        const folders = new Set<string>();
        const collect = (obj: any, prefix = "") => {
            if (!obj || typeof obj !== 'object') return;
            for (const key in obj) {
                const path = prefix ? `${prefix}/${key}` : key;
                if (obj[key] && typeof obj[key] === 'object' && obj[key].isLeafMarker !== 'true') {
                    folders.add(path);
                    collect(obj[key], path);
                }
            }
        };
        collect(schemaData);
        setExpandedPaths(folders);
    };

    const handleClearStates = () => {
        setSelectedItemType(null); setSelectedSpdhTag(null); setSelectedCtrxPath(null); setSelectedOutField(null);
    };

    const handleConnect = (connection: Connection) => {
        if (!connection.source || !connection.target) return;

        if (!validateConnection(connection)) {
            alert("Connection Rejected: Top pins must connect to Top pins. Bottom pins must connect to Bottom pins.");
            return;
        }

        const isBot = connection.sourceHandle?.includes('bot');
        const mappingType = isBot ? 'RESPONSE' : 'REQUEST';

        const spdhNode = connection.source.startsWith('spdh-') ? connection.source : (connection.target.startsWith('spdh-') ? connection.target : null);
        const ctrxNode = connection.source.startsWith('ctrx-') ? connection.source : (connection.target.startsWith('ctrx-') ? connection.target : null);
        const outboundNode = connection.source.startsWith('outbound-') ? connection.source : (connection.target.startsWith('outbound-') ? connection.target : null);

        if (spdhNode && ctrxNode && inChannelDialect !== 'NONE') {
            const spdhName = spdhNode.split('-').slice(2).join('-');
            const ctrxPath = ctrxNode.replace('ctrx-', '');

            const exists = activeLineageLinks.some(l => l.fieldName === spdhName && l.ctrxPath === ctrxPath && l.channelDialect === inChannelDialect && l.type === mappingType);
            if (exists) {
                alert(`This ${mappingType} mapping connection already exists!`);
                return;
            }

            if (!window.confirm(`Confirm [${mappingType}] Inbound Connection?\n\nFrom Tag: ${spdhName}\nTo CTRX Node: ${ctrxPath}`)) return;

            const pKey = getTypeKey(spdhName, ctrxPath, inChannelDialect, mappingType);
            const nextTypes = { ...localTypes, [pKey]: true };
            setLocalTypes(nextTypes);
            localStorage.setItem('localTypes', JSON.stringify(nextTypes));

            apiClient.saveInboundMapping({ fieldName: spdhName, ctrxPath, channelDialect: inChannelDialect, type: mappingType }).then(() => loadData());
        }

        if (ctrxNode && outboundNode && outChannelDialect !== 'NONE') {
            const ctrxPath = ctrxNode.replace('ctrx-', '');
            const outFieldName = outboundNode.replace('outbound-', '');

            const exists = activeOutboundLinks.some(l => l.fieldName === outFieldName && l.ctrxPath === ctrxPath && l.channelDialect === outChannelDialect && l.type === mappingType);
            if (exists) {
                alert(`This ${mappingType} mapping connection already exists!`);
                return;
            }

            if (!window.confirm(`Confirm [${mappingType}] Outbound Connection?\n\nFrom CTRX Node: ${ctrxPath}\nTo Outbound Field: ${outFieldName}`)) return;

            const pKey = getTypeKey(outFieldName, ctrxPath, outChannelDialect, mappingType);
            const nextTypes = { ...localTypes, [pKey]: true };
            setLocalTypes(nextTypes);
            localStorage.setItem('localTypes', JSON.stringify(nextTypes));

            apiClient.saveOutboundMapping({ fieldName: outFieldName, ctrxPath, channelDialect: outChannelDialect, type: mappingType }).then(() => loadData());
        }
    };

    const convertToManualInbound = (m: any) => {
        if (!window.confirm(`Convert this automated mapping to a manual override?`)) return;
        apiClient.saveInboundMapping({ fieldName: m.fieldName || m.fidName, ctrxPath: m.ctrxPath, channelDialect: m.channelDialect, type: m.type }).then(() => loadData());
    };

    const convertToManualOutbound = (m: any) => {
        if (!window.confirm(`Convert this automated mapping to a manual override?`)) return;
        apiClient.saveOutboundMapping({ fieldName: m.fieldName, ctrxPath: m.ctrxPath, channelDialect: m.channelDialect, type: m.type }).then(() => loadData());
    };

    const restoreAutoInboundMapping = (m: any) => {
        if (!window.confirm(`Restore automated connection?\n\nThis will permanently delete your manual override.`)) return;
        apiClient.deleteInboundMapping(m).then(() => loadData());
    };

    const restoreAutoOutboundMapping = (m: any) => {
        if (!window.confirm(`Restore automated connection?\n\nThis will permanently delete your manual override.`)) return;
        apiClient.deleteOutboundMapping(m).then(() => loadData());
    };

    const deleteInboundMapping = (m: any) => {
        if (!window.confirm(`Are you sure you want to completely delete this connection?\n\nTag: ${m.fieldName}\n➔ CTRX: ${m.ctrxPath}`)) return;
        const mapKey = getTypeKey(m.fieldName || m.fidName || '', m.ctrxPath || '', m.channelDialect || '', m.type);

        if (m.isAuto) {
            const next = [...deletedInbound, mapKey];
            setDeletedInbound(next);
            localStorage.setItem('deletedInbound', JSON.stringify(next));
        } else {
            apiClient.deleteInboundMapping(m).then(() => loadData());
            if (m.hasAuto) {
                const next = [...deletedInbound, mapKey];
                setDeletedInbound(next);
                localStorage.setItem('deletedInbound', JSON.stringify(next));
            }
        }
    };

    const deleteOutboundMapping = (m: any) => {
        if (!window.confirm(`Are you sure you want to completely delete this connection?\n\nCTRX: ${m.ctrxPath}\n➔ ISO Field: ${m.fieldName}`)) return;
        const mapKey = getTypeKey(m.fieldName || '', m.ctrxPath || '', m.channelDialect || '', m.type);

        if (m.isAuto) {
            const next = [...deletedOutbound, mapKey];
            setDeletedOutbound(next);
            localStorage.setItem('deletedOutbound', JSON.stringify(next));
        } else {
            apiClient.deleteOutboundMapping(m).then(() => loadData());
            if (m.hasAuto) {
                const next = [...deletedOutbound, mapKey];
                setDeletedOutbound(next);
                localStorage.setItem('deletedOutbound', JSON.stringify(next));
            }
        }
    };

    const handleRestoreHiddenAutos = () => {
        if (!window.confirm("Restore all automated mappings?\n\nThis will remove all manual overrides and bring back hidden connections.")) return;

        setDeletedInbound([]);
        setDeletedOutbound([]);
        localStorage.removeItem('deletedInbound');
        localStorage.removeItem('deletedOutbound');

        const inOverrides = activeLineageLinks.filter(l => !l.isAuto && l.hasAuto);
        const outOverrides = activeOutboundLinks.filter(l => !l.isAuto && l.hasAuto);

        Promise.all([
            apiClient.deleteInboundMappingsBatch(inOverrides),
            apiClient.deleteOutboundMappingsBatch(outOverrides)
        ]).then(() => loadData());
    };

    const handleNodeClick = (_e: any, node: any) => {
        const type = node.data.type;
        if (type === 'spdh') {
            setSelectedItemType('spdh');
            setSelectedSpdhTag(node.data.raw); setSelectedCtrxPath(null); setSelectedOutField(null);
            setEditingDesc(node.data.raw.description);
        } else if (type === 'outbound') {
            setSelectedItemType('outbound');
            setSelectedOutField(node.data.raw); setSelectedCtrxPath(null); setSelectedSpdhTag(null);
            setEditingDesc(node.data.raw.description);
        } else {
            const path = node.data.path as string;
            if (node.data.isLeaf) {
                setSelectedItemType('ctrx');
                setSelectedCtrxPath(path); setSelectedSpdhTag(null); setSelectedOutField(null);
                setEditingParams({ ...(node.data.meta as LeafParams) });
            } else {
                setExpandedPaths(prev => {
                    const next = new Set(prev);
                    if (next.has(path)) {
                        next.delete(path);
                        Array.from(next).forEach(p => { if (p.startsWith(path + '/')) next.delete(p); });
                    } else { next.add(path); }
                    return next;
                });
            }
        }
    };

    const handleCtrxSave = (e: React.SyntheticEvent) => { e.preventDefault(); if (!selectedCtrxPath || !editingParams) return; setIsSaving(true); apiClient.saveCtrxMetadata(selectedCtrxPath, editingParams).then(() => { setIsSaving(false); loadData(); }); };
    const handleSpdhOrOutboundSave = (e: React.SyntheticEvent) => { e.preventDefault(); setIsSaving(true); const nameKey = selectedItemType === 'spdh' ? selectedSpdhTag?.name : selectedOutField?.name; if (!nameKey) return; const savePromise = selectedItemType === 'spdh' ? apiClient.saveSpdhDescription(nameKey, editingDesc) : apiClient.saveOutboundDescription(nameKey, editingDesc); savePromise.then(() => { setIsSaving(false); loadData(); }); };

    if (loading) return <div style={{ padding: '20px', fontFamily: 'sans-serif', color: '#334155' }}>Compiling Protocol Transform Maps...</div>;

    return (
        <div style={{ width: '100vw', height: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f8fafc', overflow: 'hidden' }}>
            <style>{`
                html, body, #root { margin: 0 !important; padding: 0 !important; width: 100vw !important; max-width: none !important; height: 100vh !important; display: flex !important; flex-direction: column !important; overflow: hidden !important; background-color: #f8fafc !important; text-align: left !important; }
                
                @keyframes ctrxPulseStream { from { stroke-dashoffset: 40; } to { stroke-dashoffset: 0; } }
                @keyframes ctrxPulseStreamReverse { from { stroke-dashoffset: 0; } to { stroke-dashoffset: 40; } }
                
                .ctrx-scarlet-highway-manual-active path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStream 1.2s linear infinite !important; }
                .ctrx-blue-highway-manual-passive path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStream 3s linear infinite !important; }
                .ctrx-indigo-highway-manual-passive path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStream 3s linear infinite !important; }
                .ctrx-orange-highway-manual-active path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStream 1.2s linear infinite !important; }
                .ctrx-orange-highway-manual-passive path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStream 3s linear infinite !important; }

                .ctrx-scarlet-highway-auto-active path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStream 1.2s linear infinite !important; }
                .ctrx-blue-highway-auto-passive path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStream 3s linear infinite !important; }
                .ctrx-indigo-highway-auto-passive path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStream 3s linear infinite !important; }
                .ctrx-orange-highway-auto-active path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStream 1.2s linear infinite !important; }
                .ctrx-orange-highway-auto-passive path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStream 3s linear infinite !important; }

                .ctrx-scarlet-highway-manual-active-reverse path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStreamReverse 1.2s linear infinite !important; }
                .ctrx-blue-highway-manual-passive-reverse path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }
                .ctrx-indigo-highway-manual-passive-reverse path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }
                .ctrx-orange-highway-manual-active-reverse path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStreamReverse 1.2s linear infinite !important; }
                .ctrx-orange-highway-manual-passive-reverse path { stroke-dasharray: 8, 8 !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }

                .ctrx-scarlet-highway-auto-active-reverse path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStreamReverse 1.2s linear infinite !important; }
                .ctrx-blue-highway-auto-passive-reverse path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }
                .ctrx-indigo-highway-auto-passive-reverse path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }
                .ctrx-orange-highway-auto-active-reverse path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStreamReverse 1.2s linear infinite !important; }
                .ctrx-orange-highway-auto-passive-reverse path { stroke-dasharray: 2, 6 !important; stroke-linecap: round !important; animation: ctrxPulseStreamReverse 3s linear infinite !important; }
            `}</style>

            <TopNavigation cNodeW={cNodeW} searchQuery={searchQuery} setSearchQuery={setSearchQuery} activeDialects={activeDialects} setActiveDialects={setActiveDialects} inChannelDialect={inChannelDialect} setInChannelDialect={setInChannelDialect} outChannelDialect={outChannelDialect} setOutChannelDialect={setOutChannelDialect} handleExpandAll={handleExpandAll} handleClearStates={handleClearStates} setExpandedPaths={setExpandedPaths} hideUnmapped={hideUnmapped} setHideUnmapped={setHideUnmapped} hasHiddenAutos={hasHiddenAutos} handleRestoreHiddenAutos={handleRestoreHiddenAutos} />

            <div style={{ flexGrow: 1, width: '100%', display: 'flex', position: 'relative' }}>
                <div style={{ flexGrow: 1, height: '100%', position: 'relative' }}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        edgeTypes={edgeTypes}
                        nodeTypes={customNodeTypes}
                        onNodeClick={handleNodeClick}
                        onConnect={handleConnect}
                        isValidConnection={validateConnection}
                        connectionMode={ConnectionMode.Loose}
                        defaultViewport={{ x: 0, y: 0, zoom: 1.0 }}
                        minZoom={0.1}
                        maxZoom={1.5}
                    >
                        <Background color="#cbd5e1" gap={20} size={1} />
                        <Controls />
                    </ReactFlow>
                </div>

                <InspectorSidebar
                    selectedItemType={selectedItemType} selectedSpdhTag={selectedSpdhTag} selectedOutField={selectedOutField} selectedCtrxPath={selectedCtrxPath}
                    editingParams={editingParams} editingDesc={editingDesc} isSaving={isSaving} lineageLinks={activeLineageLinks} outboundLinks={activeOutboundLinks}
                    setEditingDesc={setEditingDesc} setEditingParams={setEditingParams} handleClearStates={handleClearStates} handleSpdhOrOutboundSave={handleSpdhOrOutboundSave} handleCtrxSave={handleCtrxSave}
                    deleteInboundMapping={deleteInboundMapping} deleteOutboundMapping={deleteOutboundMapping} convertToManualInbound={convertToManualInbound} convertToManualOutbound={convertToManualOutbound}
                    restoreAutoInboundMapping={restoreAutoInboundMapping} restoreAutoOutboundMapping={restoreAutoOutboundMapping}
                />
            </div>
        </div>
    );
}