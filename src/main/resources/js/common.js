define('poi-forms/common',[],function(){
    return {
        _debug: true,
        copyTo: function(other){
            for (var key in this){
                other[key] = this[key];
            }
        },
        debug: function(msg) {
            if (this._debug) {
                console.log(this.name, msg);
            }
        }
    };
});