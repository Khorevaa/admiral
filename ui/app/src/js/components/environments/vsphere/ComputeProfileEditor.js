/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

export default Vue.component('vsphere-compute-profile-editor', {
  template: `
    <div>
      <multicolumn-editor-group
        :headers="[
          i18n('app.environment.edit.nameLabel'),
          i18n('app.environment.edit.cpuCountLabel'),
          i18n('app.environment.edit.diskSizeMbLabel'),
          i18n('app.environment.edit.memoryMbLabel')
        ]"
        :label="i18n('app.environment.edit.instanceTypeMappingLabel')"
        :value="instanceTypeMapping"
        @change="onInstanceTypeMappingChange">
        <multicolumn-cell name="name">
          <text-control></text-control>
        </multicolumn-cell>
        <multicolumn-cell name="cpuCount">
          <number-control></number-control>
        </multicolumn-cell>
        <multicolumn-cell name="diskSizeMb">
          <number-control></number-control>
        </multicolumn-cell>
        <multicolumn-cell name="memoryMb">
          <number-control></number-control>
        </multicolumn-cell>
      </multicolumn-editor-group>
      <multicolumn-editor-group
        :headers="[
          i18n('app.environment.edit.nameLabel'),
          i18n('app.environment.edit.valueLabel')
        ]"
        :label="i18n('app.environment.edit.imageMappingLabel')"
        :value="imageMapping"
        @change="onImageMappingChange">
        <multicolumn-cell name="name">
          <text-control></text-control>
        </multicolumn-cell>
        <multicolumn-cell name="value">
          <text-control></text-control>
        </multicolumn-cell>
      </multicolumn-editor-group>
    </div>
  `,
  props: {
    endpoint: {
      required: false,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let instanceTypeMapping = this.model.instanceTypeMapping &&
        this.model.instanceTypeMapping.asMutable() || [];
    let imageTypeMapping = this.model.imageMapping &&
        this.model.imageMapping.asMutable() || [];
    return {
      instanceTypeMapping: Object.keys(instanceTypeMapping).map((key) => {
        return {
          name: key,
          cpuCount: instanceTypeMapping[key].cpuCount,
          diskSizeMb: instanceTypeMapping[key].diskSizeMb,
          memoryMb: instanceTypeMapping[key].memoryMb
        };
      }),
      imageMapping: Object.keys(imageTypeMapping).map((key) => {
        return {
          name: key,
          value: imageTypeMapping[key].image
        };
      })
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onInstanceTypeMappingChange(value) {
      this.instanceTypeMapping = value;
      this.emitChange();
    },
    onImageMappingChange(value) {
      this.imageMapping = value;
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          instanceTypeMapping: this.instanceTypeMapping.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = {
                cpuCount: current.cpuCount,
                diskSizeMb: current.diskSizeMb,
                memoryMb: current.memoryMb
              };
            }
            return previous;
          }, {}),
          imageMapping: this.imageMapping.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = {
                image: current.value
              };
            }
            return previous;
          }, {})
        },
        valid: true
      });
    }
  }
});
