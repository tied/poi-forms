define('poi-forms/config',['jquery','ajs','aui/flag','poi-forms/common'],function($,AJS,flag,CMN) {
    var o = {
        name: 'poi-forms/config'
    };
    CMN.copyTo(o);
    o.init = function() {
        o.loadRefList();
        $('#poi-forms-config').submit(function(e){
            e.preventDefault();
            $.ajax({
                url: AJS.contextPath() + '/rest/poi-forms/1.0/refdata/' + $('#poi-forms-config-refdata-group').val(),
                type: 'PUT',
                data: JSON.stringify($('#poi-forms-config-refdata-list').val().split('\n')),
                dataType: 'json',
                processData: false,
                contentType: 'application/json'
            }).done(function(data) {
                if (data.result === 'success') {
                    o.notify('success', data.message);
                    o.loadRefList();
                } else {
                    o.notify('error', data.message);
                }
            }).fail(function(jqXHR){
                o.notify('error', jqXHR.responseText);
            });
        });
    };
    o.notify = function(status,message) {
        flag({
            type: status,
            close: 'auto',
            title: AJS.I18n.getText("com.mesilat.poi-forms.config.title"),
            body: message
        });
    };
    o.loadRefList = function() {
        $.ajax({
            url: AJS.contextPath() + '/rest/poi-forms/1.0/reflist',
            dataType: 'json'
        }).done(function(data) {
            o.debug(data);
            if (data.length) {
                $('#poi-forms-config-reflist').empty();
                data.forEach(function(string){
                    $('<a>')
                        .attr('href', AJS.contextPath() + '/plugins/servlet/poiforms/refdata/' + string)
                        .text(string)
                        .click(function(e){
                            e.preventDefault();
                            o.loadRefData($(e.target).text());
                        })
                        .appendTo($('<li>').appendTo($('#poi-forms-config-reflist')));
                });
            }
        }).fail(function(jqXHR){
            o.notify('error', jqXHR.responseText);
        });
    };
    o.loadRefData = function(group) {
        $('#poi-forms-config-refdata-group').val(group);
        $.ajax({
            url: AJS.contextPath() + '/rest/poi-forms/1.0/refdata/' + group,
            dataType: 'json',
            type: 'GET'
        }).done(function(data) {
            o.debug(data);
            var text = '';
            data.forEach(function(string){
                if (text !== '') {
                    text += '\n';
                }
                text += string;
            });
            $('#poi-forms-config-refdata-list').val(text);
        }).fail(function(jqXHR){
            o.notify('error', jqXHR.responseText);
        });
    };
    o.submit = function() {
        var myXhr = $.ajaxSettings.xhr();
        if (!myXhr.upload) {
            o.notify('error', AJS.I18n.getText("com.mesilat.poi-forms.error.not-fileupload"));
            return;
        }
        $.ajax({
            url: AJS.contextPath() + '/plugins/servlet/poiforms/refdata',
            type: 'POST',
            dataType: 'json',
            xhr: function () {
                myXhr = $.ajaxSettings.xhr();
                //if (myXhr.upload) {
                //    myXhr.upload.addEventListener('progress', progressHandlingFunction, false); // progressbar
                //}
                return myXhr;
            },
            data: new FormData($('#poi-forms-config-refdata')[0]),
            cache: false,
            contentType: false,
            processData: false
        }).done(function(data) {
            if (data.result === 'success') {
                o.loadRefList();
                o.notify('success', AJS.I18n.getText("com.mesilat.poi-forms.msg.file-upload-success"));
            } else {
                o.notify('error', data.message);
            }
        }).fail(function (jqXHR) {
            o.notify('error', jqXHR.responseText);
        });
    };
    return o;
});

(function(AJS,$){
    $(function(){
        console.log('poi-forms', 'initializing...');

        if ($('#poi-forms-config').length) {
            require(['poi-forms/config'], function(CNF){
                CNF.init();
            });
        }

        $('div.poi-form').each(function(){
            var client = require('poi-client');
            var div = $(this);
            var file = div.attr('file');
            var protection = div.attr('protection') === 'true';
            div.empty();
            var divSheets = $('<div class="poi-sheets">')
                .text(AJS.I18n.getText("com.mesilat.poi-forms.poi-form.reading"))
                .appendTo(div);
            var divTable  = $('<div class="poi-table">')
                .text(AJS.I18n.getText("com.mesilat.poi-forms.poi-form.click"))
                .appendTo(div);
            div[0].__poiClient = new client.PoiClient(AJS.params.pageId,file,divSheets,divTable, protection);
            div[0].__poiClient.openLocal(AJS.contextPath() + '/rest/poi-forms/1.0/form');
        });
    });
})(AJS,AJS.$||$);