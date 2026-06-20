Component({
  properties: {
    visible: {
      type: Boolean,
      value: false
    },
    reminder: {
      type: Object,
      value: {}
    }
  },

  methods: {
    onTaken() {
      this.triggerEvent("taken", { reminder: this.data.reminder });
    },

    onLater() {
      this.triggerEvent("later", { reminder: this.data.reminder });
    }
  }
});
