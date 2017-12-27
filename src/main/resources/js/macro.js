(function(AJS,$){
    function setupFileParam() {
        $.ajax({
            url: AJS.contextPath() + '/rest/api/content/' + AJS.params.pageId + '/child/attachment',
            type: 'GET',
            dataType: 'json'
        }).done(function(data){
            var _data = [];
            data.results.forEach(function(rec){
                _data.push({
                    id: rec.title,
                    text: rec.title
                });
            });
            console.log('poi-forms', _data);
            var $input = $('<input type="hidden" class="macro-param-input" id="macro-param-file">');
            var val = $('#macro-param-file').val();
            $('#macro-param-file').replaceWith($input);
            $input.auiSelect2({
                data: _data
            });
            $input.on('select2-selecting', function(e){
                $('button.button-panel-button.ok').prop('disabled', false);
            });
            if (val !== ''){
                $input
                    .val(val)
                    .closest('div.macro-param-div').find('span.select2-chosen').text(val);
            }
        }).fail(function(jqxhr){
            console.log('poi-forms', jqxhr.responseText);
        });

        $('#macro-param-div-file .macro-param-desc a').on('click', function(e){
            e.preventDefault();
            e.stopImmediatePropagation();
            alert('Create sample file');
        });
    }

    AJS.MacroBrowser.setMacroJsOverride('poi-form', {
        beforeParamsSet: function (selectedParams, macroSelected) {
            setupFileParam();
            return selectedParams;
        }
    });
})(AJS,AJS.$||$);
