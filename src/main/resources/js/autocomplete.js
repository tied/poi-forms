define('com.mesilat:poi-autocomplete', ['jquery'], function($){
    function filter(autocomplete, text, list){
        function setOptions(data){
            if (data.length > 0) {
                list.empty();
                data.forEach(function(val){
                    $('<li>').text(val).prop('tabindex', 1).appendTo(list);
                });
                list.show();

                list.find('li').each(function(){
                    if (list.width() < $(this).width() + 14){
                        list.width($(this).width() + 14);
                    }
                });
            } else {
                list.hide();
            }
        }

        var uText = text.toUpperCase();
        var maxValues = 10;
        if ('maxValues' in autocomplete){
            maxValues =  autocomplete.maxValues;
        }

        if ('local' in autocomplete){
            var data = [];
            for (var i = 0; i < autocomplete.local.length; i++){
                if (autocomplete.local[i].toUpperCase().startsWith(uText)){
                    data.push(autocomplete.local[i]);
                }
                if (data.length >= maxValues){
                    break;
                }
            }
            setOptions(data);
        } else if ('remote' in autocomplete && typeof autocomplete.remote === 'function') {
            autocomplete.remote(text, function(data){
                setOptions(data);
            });
        }
    }

    return {
        filter: filter
    };
});