import React, { useMemo } from 'react';
import { Position, Handle } from '@xyflow/react';
import type { Node, Edge, NodeProps } from '@xyflow/react';

export interface LeafParams {
    dataType: string;
    length: string;
    dbTable: string;
    exampleValue: string;
    description: string;
    isLeafMarker?: string;
}

export interface CommonElement {
    name: string;
    tag: string;
    description: string;
    dialect?: string;
}

export interface VisibleItem {
    id: string;
    label: string;
    path: string;
    isLeaf: boolean;
    level: number;
    parentId: string | null;
    meta: LeafParams | null;
}

export const sNodeW = 325;
export const cNodeW = 355;
export const oNodeW = 325;
export const indentW = 60;
export const laneGap = 280;

export const ROW_HEIGHT = 40;
export const ROW_STRIDE = 48;

export const customNodeTypes = {
    spdh: ({ data }: NodeProps) => (
        <>
            {data.showHandles && (
                <>
                    <Handle type="source" position={Position.Right} id="top" style={{ top: '25%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', right: '-4px', zIndex: 10 }} />
                    <Handle type="source" position={Position.Right} id="bot" style={{ top: '75%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', right: '-4px', zIndex: 10 }} />
                </>
            )}
            {data.label as React.ReactNode}
        </>
    ),
    ctrx: ({ data }: NodeProps) => (
        <>
            {data.showHandles && (
                <>
                    <Handle type="target" position={Position.Left} id="top-in" style={{ top: '25%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', left: '-4px', zIndex: 10 }} />
                    <Handle type="target" position={Position.Left} id="bot-out" style={{ top: '75%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', left: '-4px', zIndex: 10 }} />

                    <Handle type="source" position={Position.Right} id="top-out" style={{ top: '25%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', right: '-4px', zIndex: 10 }} />
                    <Handle type="source" position={Position.Right} id="bot-in" style={{ top: '75%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', right: '-4px', zIndex: 10 }} />
                </>
            )}
            {data.label as React.ReactNode}
        </>
    ),
    outbound: ({ data }: NodeProps) => (
        <>
            {data.showHandles && (
                <>
                    <Handle type="target" position={Position.Left} id="top" style={{ top: '25%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', left: '-4px', zIndex: 10 }} />
                    <Handle type="target" position={Position.Left} id="bot" style={{ top: '75%', background: '#475569', width: '8px', height: '8px', border: '1px solid #fff', left: '-4px', zIndex: 10 }} />
                </>
            )}
            {data.label as React.ReactNode}
        </>
    )
};

export const checkInboundDialect = (_fieldName: string) => {
    return 'Dynamic Dialect';
};

export const getDialectColor = (dialectName: string) => {
    const shortName = dialectName.replace('SpdhTag', '').replace('V1', '');
    const palette: Record<string, { bg: string, text: string, border: string }> = {
        'Base': { bg: '#dbeafe', text: '#1e40af', border: '#bfdbfe' },
        'Fest': { bg: '#fce7f3', text: '#9d174d', border: '#fbcfe8' },
        'Mos': { bg: '#dcfce7', text: '#166534', border: '#bbf7d0' },
        'Nusz': { bg: '#fef3c7', text: '#9a3412', border: '#fde68a' },
        'PosMol': { bg: '#e0e7ff', text: '#3730a3', border: '#c7d2fe' },
        'QrPayment': { bg: '#ffedd5', text: '#9a3412', border: '#fcd34d' },
        'SShop': { bg: '#fae8ff', text: '#86198f', border: '#f5d0fe' },
        'TopUpMobile': { bg: '#ccfbf1', text: '#115e59', border: '#99f6e4' },
        'Upc': { bg: '#f3e8ff', text: '#701a75', border: '#e9d5ff' }
    };
    return palette[shortName] || { bg: '#f1f5f9', text: '#475569', border: '#e2e8f0' };
};

const branchContainsMatch = (obj: any, query: string, prefix = ''): boolean => {
    if (!query) return true;
    if (!obj || typeof obj !== 'object') return false;
    for (const key in obj) {
        const totalPath = prefix ? `${prefix}/${key}` : key;
        const val = obj[key];
        const isLeaf = val && val.isLeafMarker === 'true';
        const currentMatches = key.toLowerCase().includes(query.toLowerCase()) || totalPath.toLowerCase().includes(query.toLowerCase()) || (isLeaf && val.description && val.description.toLowerCase().includes(query.toLowerCase())) || (isLeaf && val.dbTable && val.dbTable.toLowerCase().includes(query.toLowerCase()));
        if (currentMatches) return true;
        if (!isLeaf && typeof val === 'object' && branchContainsMatch(val, query, totalPath)) return true;
    }
    return false;
};

export interface EngineParams {
    schemaData: any;
    spdhTagsList: CommonElement[];
    outFieldsList: CommonElement[];
    expandedPaths: Set<string>;
    searchQuery: string;
    selectedItemType: 'ctrx' | 'spdh' | 'outbound' | null;
    selectedSpdhTag: CommonElement | null;
    selectedCtrxPath: string | null;
    selectedOutField: CommonElement | null;
    activeViewportSpaceX: number;
    inChannelDialect: string;
    outChannelDialect: string;
    lineageLinks: any[];
    outboundLinks: any[];
    hideUnmapped: boolean;
}

export const useMappingEngine = (params: EngineParams) => {
    const {
        schemaData, spdhTagsList, outFieldsList, expandedPaths, searchQuery,
        selectedItemType, selectedSpdhTag, selectedCtrxPath, selectedOutField,
        activeViewportSpaceX, inChannelDialect, outChannelDialect,
        lineageLinks, outboundLinks, hideUnmapped
    } = params;

    return useMemo(() => {
        const treeStartX = (activeViewportSpaceX - cNodeW) / 2;
        const baseAnchorX = treeStartX - laneGap - sNodeW;
        const showHandles = inChannelDialect !== 'NONE' || outChannelDialect !== 'NONE';

        const generatedNodes: Node[] = [];
        const backgroundEdges: Edge[] = [];
        const activeEdges: Edge[] = [];

        if (!schemaData) return { nodes: [], edges: [] };

        let visibleSpdhTags = spdhTagsList.filter(t => {
            if (!t) return false;
            if (!searchQuery) return true;
            const q = searchQuery.toLowerCase();
            return t.name.toLowerCase().includes(q) || t.tag.toLowerCase().includes(q) || (t.description && t.description.toLowerCase().includes(q));
        });

        visibleSpdhTags.sort((a, b) => a.tag.localeCompare(b.tag));

        let visibleOutFields = outFieldsList.filter(f => {
            if (!f) return false;
            if (!searchQuery) return true;
            const q = searchQuery.toLowerCase();
            return f.name.toLowerCase().includes(q) || f.tag.toLowerCase().includes(q) || (f.description && f.description.toLowerCase().includes(q));
        });

        let ctrxItems: VisibleItem[] = [];
        let maxVisibleCtrxLevel = 0;

        const traverseCtrx = (obj: any, prefix = '', level = 0, parentId: string | null = null) => {
            if (!obj || typeof obj !== 'object') return;
            for (const key in obj) {
                const totalPath = prefix ? `${prefix}/${key}` : key;
                const val = obj[key];
                const isLeaf = val && val.isLeafMarker === 'true';

                let shouldInclude = false;
                if (!searchQuery) shouldInclude = true;
                else {
                    const nodeMatches = key.toLowerCase().includes(searchQuery.toLowerCase()) || totalPath.toLowerCase().includes(searchQuery.toLowerCase()) || (isLeaf && val.description && val.description.toLowerCase().includes(searchQuery.toLowerCase())) || (isLeaf && val.dbTable && val.dbTable.toLowerCase().includes(searchQuery.toLowerCase()));
                    if (nodeMatches) shouldInclude = true;
                    else if (!isLeaf && typeof val === 'object') shouldInclude = branchContainsMatch(val, searchQuery, totalPath);
                }
                if (!shouldInclude) continue;

                if (level > maxVisibleCtrxLevel) maxVisibleCtrxLevel = level;
                const isExpanded = searchQuery ? true : expandedPaths.has(totalPath);
                ctrxItems.push({ id: `ctrx-${totalPath}`, label: isLeaf ? `📄 ${key}` : `${isExpanded ? '▼' : '▶'} 📁 ${key}`, path: totalPath, isLeaf, level, parentId, meta: isLeaf ? val : null });
                if (!isLeaf && (isExpanded || searchQuery) && typeof val === 'object') traverseCtrx(val, totalPath, level + 1, `ctrx-${totalPath}`);
            }
        };
        traverseCtrx(schemaData);

        const inboundWires = lineageLinks.map(l => {
            if (!l || !l.ctrxPath) return null;
            if (inChannelDialect === 'NONE' || (l.channelDialect && l.channelDialect !== inChannelDialect)) return null;

            const fName = l.fieldName || l.fidName || '';
            let bestMatch: VisibleItem | null = null;
            for (const item of ctrxItems) {
                if (l.ctrxPath.toLowerCase() === item.path.toLowerCase() || l.ctrxPath.toLowerCase().startsWith(item.path.toLowerCase() + '/')) {
                    if (!bestMatch || item.level > bestMatch.level) bestMatch = item;
                }
            }
            const segments = l.ctrxPath.split('/');
            let currentCursor = schemaData;
            let semanticLeafDef = false;
            for (const segment of segments) {
                if (currentCursor && typeof currentCursor === 'object' && currentCursor[segment] !== undefined) currentCursor = currentCursor[segment];
                else break;
            }
            if (currentCursor && currentCursor.isLeafMarker === 'true') semanticLeafDef = true;

            return bestMatch ? { fieldName: fName, resolvedTargetId: bestMatch.id, targetsParentDirectly: !semanticLeafDef, isAuto: !!l.isAuto, type: l.type || 'REQUEST' } : null;
        }).filter(w => w !== null);

        const outboundWires = outboundLinks.map(l => {
            if (!l || !l.ctrxPath) return null;
            if (outChannelDialect === 'NONE' || (l.channelDialect && l.channelDialect !== outChannelDialect)) return null;

            const fName = l.fieldName || '';
            let bestMatch: VisibleItem | null = null;
            for (const item of ctrxItems) {
                if (l.ctrxPath.toLowerCase() === item.path.toLowerCase() || l.ctrxPath.toLowerCase().startsWith(item.path.toLowerCase() + '/')) {
                    if (!bestMatch || item.level > bestMatch.level) bestMatch = item;
                }
            }
            const segments = l.ctrxPath.split('/');
            let currentCursor = schemaData;
            let semanticLeafDef = false;
            for (const segment of segments) {
                if (currentCursor && typeof currentCursor === 'object' && currentCursor[segment] !== undefined) currentCursor = currentCursor[segment];
                else break;
            }
            if (currentCursor && currentCursor.isLeafMarker === 'true') semanticLeafDef = true;

            return bestMatch ? { fieldName: fName, resolvedSourceId: bestMatch.id, targetsParentDirectly: !semanticLeafDef, isAuto: !!l.isAuto, type: l.type || 'REQUEST' } : null;
        }).filter(w => w !== null);

        if (hideUnmapped) {
            const mappedCtrxIds = new Set<string>();
            inboundWires.forEach(w => { if (w) mappedCtrxIds.add(w.resolvedTargetId); });
            outboundWires.forEach(w => { if (w) mappedCtrxIds.add(w.resolvedSourceId); });

            const pathsToKeep = new Set<string>();
            mappedCtrxIds.forEach(id => {
                const item = ctrxItems.find(i => i.id === id);
                if (item) {
                    let current = item.path;
                    pathsToKeep.add(current);
                    while (current.includes('/')) {
                        current = current.substring(0, current.lastIndexOf('/'));
                        pathsToKeep.add(current);
                    }
                }
            });
            ctrxItems = ctrxItems.filter(item => pathsToKeep.has(item.path));

            const mappedSpdhNames = new Set<string>();
            inboundWires.forEach(w => { if (w) mappedSpdhNames.add(w.fieldName.toLowerCase()); });
            visibleSpdhTags = visibleSpdhTags.filter(t => mappedSpdhNames.has(t.name.toLowerCase()));

            const mappedOutNames = new Set<string>();
            outboundWires.forEach(w => { if (w) mappedOutNames.add(w.fieldName.toLowerCase()); });
            visibleOutFields = visibleOutFields.filter(f => mappedOutNames.has(f.name.toLowerCase()));
        }

        const totalTreeW = cNodeW + (maxVisibleCtrxLevel * indentW);
        const outStartX = treeStartX + totalTreeW + laneGap;

        const activeFids = new Set<string>();
        const activeCtrxPaths = new Set<string>();
        const activeOutFields = new Set<string>();

        if (selectedItemType === 'spdh' && selectedSpdhTag) {
            const curSpdhLower = selectedSpdhTag.name.toLowerCase();
            activeFids.add(curSpdhLower);
            inboundWires.forEach(w => { if (w && w.fieldName.toLowerCase() === curSpdhLower) activeCtrxPaths.add(w.resolvedTargetId.replace('ctrx-', '').toLowerCase()); });
            outboundWires.forEach(w => { if (w && activeCtrxPaths.has(w.resolvedSourceId.replace('ctrx-', '').toLowerCase())) activeOutFields.add(w.fieldName.toLowerCase()); });
        } else if (selectedItemType === 'outbound' && selectedOutField) {
            const curOutLower = selectedOutField.name.toLowerCase();
            activeOutFields.add(curOutLower);
            outboundWires.forEach(w => { if (w && w.fieldName.toLowerCase() === curOutLower) activeCtrxPaths.add(w.resolvedSourceId.replace('ctrx-', '').toLowerCase()); });
            inboundWires.forEach(w => { if (w && activeCtrxPaths.has(w.resolvedTargetId.replace('ctrx-', '').toLowerCase())) activeFids.add(w.fieldName.toLowerCase()); });
        } else if (selectedItemType === 'ctrx' && selectedCtrxPath) {
            const curCtrxLower = selectedCtrxPath.toLowerCase();
            activeCtrxPaths.add(curCtrxLower);
            inboundWires.forEach(w => { if (w && w.resolvedTargetId.replace('ctrx-', '').toLowerCase() === curCtrxLower) activeFids.add(w.fieldName.toLowerCase()); });
            outboundWires.forEach(w => { if (w && w.resolvedSourceId.replace('ctrx-', '').toLowerCase() === curCtrxLower) activeOutFields.add(w.fieldName.toLowerCase()); });
        }

        visibleSpdhTags.forEach((tag, idx) => {
            const isSelected = selectedSpdhTag?.name === tag.name && selectedSpdhTag?.dialect === tag.dialect;
            const isHighlighted = isSelected || activeFids.has(tag.name.toLowerCase());

            const prev = visibleSpdhTags[idx - 1];
            const next = visibleSpdhTags[idx + 1];
            const isDuplicate = (prev && prev.tag === tag.tag) || (next && next.tag === tag.tag);

            const shortDialect = tag.dialect ? tag.dialect.replace('SpdhTag', '').replace('V1', '') : '';
            const pillColors = tag.dialect ? getDialectColor(tag.dialect) : getDialectColor('DEFAULT');

            generatedNodes.push({
                id: `spdh-${tag.dialect}-${tag.name}`,
                position: { x: baseAnchorX, y: idx * ROW_STRIDE + 100 },
                type: 'spdh',
                data: {
                    showHandles,
                    label: (
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', height: '100%' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', overflow: 'hidden' }}>
                                <span style={{ color: isHighlighted ? '#ffffff' : '#0369a1', fontSize: '13px', fontFamily: 'monospace', fontWeight: '900' }}>[{tag.tag}]</span>
                                <span style={{ textOverflow: 'ellipsis', whiteSpace: 'nowrap', overflow: 'hidden', color: isHighlighted ? '#e0f2fe' : '#64748b', fontWeight: '600' }}>{tag.name}</span>
                            </div>
                            {shortDialect && (
                                <div style={{ background: isHighlighted ? '#ffffff' : pillColors.bg, color: isHighlighted ? '#0284c7' : pillColors.text, padding: '2px 6px', borderRadius: '4px', fontSize: '9px', fontWeight: 'bold', textTransform: 'uppercase', border: isHighlighted ? '1px solid #e0f2fe' : `1px solid ${pillColors.border}`, flexShrink: 0 }}>
                                    {shortDialect}
                                </div>
                            )}
                        </div>
                    ),
                    type: 'spdh',
                    raw: tag
                },
                style: {
                    background: isHighlighted ? '#38bdf8' : '#f0f9ff',
                    border: isDuplicate ? '2px solid #ef4444' : (isHighlighted ? '2px solid #0284c7' : '1px solid #bae6fd'),
                    color: isHighlighted ? '#ffffff' : '#0369a1', borderRadius: '6px', height: `${ROW_HEIGHT}px`, padding: '0 12px', display: 'flex', alignItems: 'center', boxSizing: 'border-box', fontSize: '12px', width: sNodeW, cursor: 'pointer',
                    boxShadow: isHighlighted ? '0 0 18px rgba(56, 189, 248, 0.65)' : 'none', transition: 'all 0.2s ease-in-out'
                }
            });
        });

        ctrxItems.forEach((item, rowIndex) => {
            const isSelected = selectedCtrxPath === item.path;
            const isHighlighted = isSelected || activeCtrxPaths.has(item.path.toLowerCase());
            generatedNodes.push({
                id: item.id, position: { x: treeStartX + (item.level * indentW), y: rowIndex * ROW_STRIDE + 100 },
                type: 'ctrx',
                data: { label: item.label, path: item.path, isLeaf: item.isLeaf, meta: item.meta, type: 'ctrx', showHandles },
                style: {
                    background: item.isLeaf ? (isHighlighted ? '#34d399' : '#ffffff') : (isHighlighted ? '#10b981' : '#f0fdf4'),
                    border: item.isLeaf ? (isHighlighted ? '2px solid #047857' : '1px solid #cbd5e1') : (isHighlighted ? '2px solid #065f46' : '1px solid #6ee7b7'),
                    color: item.isLeaf ? (isHighlighted ? '#ffffff' : '#334155') : (isHighlighted ? '#ffffff' : '#14532d'),
                    borderRadius: '6px', height: `${ROW_HEIGHT}px`, padding: '0 12px', display: 'flex', alignItems: 'center', boxSizing: 'border-box', fontSize: '12px', width: cNodeW, cursor: 'pointer',
                    boxShadow: isHighlighted ? '0 0 18px rgba(52, 211, 153, 0.65)' : 'none', fontWeight: item.isLeaf ? (isHighlighted ? 'bold' : 'normal') : 'bold', transition: 'all 0.2s ease-in-out'
                }
            });
        });

        visibleOutFields.forEach((field, idx) => {
            const isSelected = selectedOutField?.name === field.name;
            const isHighlighted = isSelected || activeOutFields.has(field.name.toLowerCase());
            generatedNodes.push({
                id: `outbound-${field.name}`, position: { x: outStartX, y: idx * ROW_STRIDE + 100 },
                type: 'outbound',
                data: { label: field.name, type: 'outbound', raw: field, showHandles },
                style: {
                    background: isHighlighted ? '#818cf8' : '#f5f3ff', border: isHighlighted ? '2px solid #4338ca' : '1px solid #c7d2fe',
                    color: isHighlighted ? '#ffffff' : '#4338ca', borderRadius: '6px', height: `${ROW_HEIGHT}px`, padding: '0 12px', display: 'flex', alignItems: 'center', boxSizing: 'border-box', fontSize: '12px', fontWeight: 'bold', width: oNodeW, cursor: 'pointer',
                    boxShadow: isHighlighted ? '0 0 18px rgba(129, 140, 248, 0.65)' : 'none', transition: 'all 0.2s ease-in-out'
                }
            });
        });

        const activeNodeRegistry = new Set(generatedNodes.map(n => n.id));

        inboundWires.forEach((wire, idx) => {
            if (!wire) return;
            const targetId = wire.resolvedTargetId;
            const type = wire.type;

            const matchingSpdhNodes = generatedNodes.filter(n => n.data.type === 'spdh' && (n.data.raw as any).name === wire.fieldName);

            matchingSpdhNodes.forEach((srcNode, matchIdx) => {
                const sourceId = srcNode.id;
                if (activeNodeRegistry.has(sourceId) && activeNodeRegistry.has(targetId)) {
                    const isActive = activeFids.has(wire.fieldName.toLowerCase()) && activeCtrxPaths.has(targetId.replace('ctrx-', '').toLowerCase());
                    const slot = idx % 5; const routeX = baseAnchorX + sNodeW + 40 + (slot * 45);
                    const targetsParent = wire.targetsParentDirectly;
                    const typeModifier = wire.isAuto ? 'auto' : 'manual';

                    const isReq = wire.type !== 'RESPONSE';
                    const strokeColor = isReq ? (isActive ? '#22c55e' : '#4ade80') : (isActive ? '#ef4444' : '#f87171');

                    const className = targetsParent
                        ? (isActive ? `ctrx-orange-highway-${typeModifier}-active${isReq ? '' : '-reverse'}` : `ctrx-orange-highway-${typeModifier}-passive${isReq ? '' : '-reverse'}`)
                        : (isActive ? `ctrx-scarlet-highway-${typeModifier}-active${isReq ? '' : '-reverse'}` : `ctrx-blue-highway-${typeModifier}-passive${isReq ? '' : '-reverse'}`);

                    const edgeSourceHandle = isReq ? 'top' : 'bot';
                    const edgeTargetHandle = isReq ? 'top-in' : 'bot-out'; // <-- FIXED: Pointing to CTRX Left-Side Target

                    const wirePayload: Edge = {
                        id: `inbound-highway-wire-${type}-${idx}-${matchIdx}`,
                        source: sourceId, target: targetId,
                        sourceHandle: edgeSourceHandle, targetHandle: edgeTargetHandle,
                        type: 'routed', data: { routeX },
                        className, style: { stroke: strokeColor, strokeWidth: isActive ? 3.0 : 2.0, opacity: isActive ? 1.0 : 0.45 }
                    };
                    if (isActive) activeEdges.push(wirePayload); else backgroundEdges.push(wirePayload);
                }
            });
        });

        outboundWires.forEach((wire, idx) => {
            if (!wire) return;
            const sourceId = wire.resolvedSourceId;
            const targetId = `outbound-${wire.fieldName}`;
            const type = wire.type;

            if (activeNodeRegistry.has(sourceId) && activeNodeRegistry.has(targetId)) {
                const isActive = activeCtrxPaths.has(sourceId.replace('ctrx-', '').toLowerCase()) && activeOutFields.has(wire.fieldName.toLowerCase());
                const slot = idx % 5; const routeX = treeStartX + totalTreeW + 40 + (slot * 45);
                const targetsParent = wire.targetsParentDirectly;
                const typeModifier = wire.isAuto ? 'auto' : 'manual';

                const isReq = wire.type !== 'RESPONSE';
                const strokeColor = isReq ? (isActive ? '#22c55e' : '#4ade80') : (isActive ? '#ef4444' : '#f87171');

                const className = targetsParent
                    ? (isActive ? `ctrx-orange-highway-${typeModifier}-active${isReq ? '' : '-reverse'}` : `ctrx-orange-highway-${typeModifier}-passive${isReq ? '' : '-reverse'}`)
                    : (isActive ? `ctrx-scarlet-highway-${typeModifier}-active${isReq ? '' : '-reverse'}` : `ctrx-indigo-highway-${typeModifier}-passive${isReq ? '' : '-reverse'}`);

                const edgeSourceHandle = isReq ? 'top-out' : 'bot-in'; // <-- FIXED: Pointing to CTRX Right-Side Source
                const edgeTargetHandle = isReq ? 'top' : 'bot';

                const wirePayload: Edge = {
                    id: `outbound-highway-wire-${type}-${idx}`,
                    source: sourceId, target: targetId,
                    sourceHandle: edgeSourceHandle, targetHandle: edgeTargetHandle,
                    type: 'routed', data: { routeX },
                    className, style: { stroke: strokeColor, strokeWidth: isActive ? 3.0 : 2.0, opacity: isActive ? 1.0 : 0.45 }
                };
                if (isActive) activeEdges.push(wirePayload); else backgroundEdges.push(wirePayload);
            }
        });

        return { nodes: generatedNodes, edges: [...backgroundEdges, ...activeEdges] };
    }, [
        schemaData, spdhTagsList, outFieldsList, expandedPaths, searchQuery,
        selectedItemType, selectedSpdhTag, selectedCtrxPath, selectedOutField,
        activeViewportSpaceX, inChannelDialect, outChannelDialect,
        lineageLinks, outboundLinks, hideUnmapped
    ]);
};