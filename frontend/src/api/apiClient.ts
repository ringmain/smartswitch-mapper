const BASE_URL = 'http://localhost:8080/api';

export const apiClient = {
    loadWorkspaceData: async (activeDialects: string[], inDialect: string, outDialect: string) => {
        // Send as comma-separated list for Spring Boot
        const dialectsParam = activeDialects.length > 0 ? activeDialects.join(',') : 'NONE';

        const [schema, tags, outFields, transLinks, outLinks] = await Promise.all([
            fetch(`${BASE_URL}/schema`).then(res => res.json()),
            fetch(`${BASE_URL}/spdh-tags?dialects=${dialectsParam}`).then(res => res.json()),
            fetch(`${BASE_URL}/outbound-fields?dialect=BIC_ISO`).then(res => res.json()),
            fetch(`${BASE_URL}/transformer-mappings?dialect=${inDialect}`).then(res => res.json()),
            fetch(`${BASE_URL}/outbound-mappings?dialect=${outDialect}`).then(res => res.json())
        ]);
        return { schema, tags, outFields, transLinks, outLinks };
    },

    saveCtrxMetadata: async (path: string, parameters: any) => {
        return fetch(`${BASE_URL}/metadata`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ path, parameters }) });
    },

    saveSpdhDescription: async (name: string, description: string) => {
        return fetch(`${BASE_URL}/spdh-description`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description }) });
    },

    saveOutboundDescription: async (name: string, description: string) => {
        return fetch(`${BASE_URL}/outbound-description`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description }) });
    },

    saveInboundMapping: async (mapping: any) => {
        return fetch(`${BASE_URL}/transformer-mappings`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mapping) }).then(res => res.json());
    },

    deleteInboundMapping: async (mapping: any) => {
        return fetch(`${BASE_URL}/transformer-mappings/delete`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mapping) });
    },

    deleteInboundMappingsBatch: async (mappings: any[]) => {
        if (!mappings.length) return Promise.resolve();
        return fetch(`${BASE_URL}/transformer-mappings/delete-batch`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mappings) });
    },

    saveOutboundMapping: async (mapping: any) => {
        return fetch(`${BASE_URL}/outbound-mappings`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mapping) }).then(res => res.json());
    },

    deleteOutboundMapping: async (mapping: any) => {
        return fetch(`${BASE_URL}/outbound-mappings/delete`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mapping) });
    },

    deleteOutboundMappingsBatch: async (mappings: any[]) => {
        if (!mappings.length) return Promise.resolve();
        return fetch(`${BASE_URL}/outbound-mappings/delete-batch`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mappings) });
    }
};