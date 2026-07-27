(function() {
    const patchedMaterials = new Map();
    let renderFrameCallback;

    function isTransparentParticleMaterial(material) {
        const materialType = material?.uniforms?.materialType?.value;
        const particleShader = typeof material?.fragmentShader === 'string' &&
            material.fragmentShader.includes('tColor=texture2D(map,vUv)');

        // Blockbench maps particles_blend to 2 and particles_add to 3.
        return particleShader && (materialType === 2 || materialType === 3);
    }

    function patchParticleDepthWrite() {
        if (!globalThis.Canvas?.scene?.traverse) return;

        Canvas.scene.traverse(object => {
            const materials = Array.isArray(object.material)
                ? object.material
                : object.material ? [object.material] : [];

            materials.forEach(material => {
                if (!isTransparentParticleMaterial(material)) return;

                if (!patchedMaterials.has(material)) {
                    patchedMaterials.set(material, {
                        depthTest: material.depthTest,
                        depthWrite: material.depthWrite
                    });
                }

                // Transparent pixels must not become an invisible depth mask.
                material.depthTest = true;
                material.depthWrite = false;
                material.needsUpdate = true;
            });
        });
    }

    Plugin.register('destiny_particle_depth_fix', {
        title: 'Destiny Particle Depth Fix',
        author: 'Destiny 2 Mod Project',
        description: 'Prevents transparent Bedrock add/blend particles from hiding model geometry in Blockbench.',
        icon: 'auto_fix_high',
        version: '1.0.0',
        variant: 'desktop',
        min_version: '4.8.0',

        onload() {
            renderFrameCallback = patchParticleDepthWrite;
            Blockbench.on('render_frame', renderFrameCallback);
            patchParticleDepthWrite();
        },

        onunload() {
            if (renderFrameCallback) {
                Blockbench.removeListener('render_frame', renderFrameCallback);
            }
            patchedMaterials.forEach((state, material) => {
                material.depthTest = state.depthTest;
                material.depthWrite = state.depthWrite;
                material.needsUpdate = true;
            });
            patchedMaterials.clear();
        }
    });
})();
