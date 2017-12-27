(function(AJS,$){
    $(function(){
        var params = ['max-files','max-bytes'];

        $('form.poi-settings').each(function(){
            var $form = $(this);
            $.ajax({
                url: AJS.contextPath() + '/rest/poi-forms/1.0/settings',
                type: 'GET',
                datatype: 'json'
            }).done(function(data){
                params.forEach(function(param){
                    if (param in data){
                        $form.find('[name="' + param + '"]').val(data[param]);
                    }
                });
            }).fail(function(jqxhr){
                console.log('poi-forms', jqxhr.responseText);
            });

            $form.find('input.submit').on('click', function(e){
                e.preventDefault();
                var data = {};
                params.forEach(function(param){
                    if ($form.find('[name="' + param + '"]').val() !== ''){
                        data[param] = $form.find('[name="' + param + '"]').val();
                    }
                });
                $.ajax({
                    url: AJS.contextPath() + '/rest/poi-forms/1.0/settings',
                    type: 'PUT',
                    datatype: 'text',
                    data: JSON.stringify(data),
                    processData: false,
                    contentType: 'application/json'
                }).done(function(text){
                    $form
                        .find('div.aui-message')
                        .html($('<p>').text(text))    
                        .css('display', 'block')
                        .removeClass()
                        .addClass('aui-message aui-message-success fadeout');
                    setTimeout(function(){
                        $form.find('.fadeout').each(function(){
                            $(this).removeClass('fadeout');
                            $(this).hide(500);
                        });
                    }, 2000);
                }).fail(function(jqxhr){
                    $form
                        .find('div.aui-message')
                        .html($('<p>').text(jqxhr.responseText))    
                        .css('display', 'block')
                        .removeClass()
                        .addClass('aui-message aui-message-error fadeout');
                    setTimeout(function(){
                        $form.find('.fadeout').each(function(){
                            $(this).removeClass('fadeout');
                            $(this).hide(500);
                        });
                    }, 2000);
                });
            });
        });
    });
})(AJS,AJS.$||$);