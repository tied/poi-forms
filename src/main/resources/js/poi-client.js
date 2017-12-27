define('poi-client',['ajs','jquery','moment','aui/flag','com.mesilat:poi-autocomplete','com.mesilat:format'],function(AJS,$,moment,flag,autocomplete,ssf){
    var cloneProperties = ['padding', 'padding-top', 'padding-bottom', 'padding-left', 'padding-right',
        'text-align', 'font', 'font-size', 'font-family', 'font-weight',
        'border', 'border-top', 'border-bottom', 'border-left', 'border-right'];

    var flickering = [0,0.31,0.59,0.81,0.95,1,0.95,0.81,0.59,0.31];
    var o = {
        name: 'com.mesilat:poi-client',
        locale: window.navigator.userLanguage || window.navigator.language,
        debug: true,
        autocomplete: autocomplete,
        pollingInterval: 3000,
        flickering: true
    };
    o.idCounter = 0;
    o.uniqueId = function(){
        return 'poi-id-' + (o.idCounter++);
    };
    o.init = function(settings){
        for (var key in settings) {
            o[key] = settings[key];
        }
        //moment.locale(o.locale);
    };
//    o.__autocomplete = {};
    o.__debug = function(args){
        if (o.debug) {
            console.log('poi-forms debug', args);
        }
    };
    o.notifyError = function(message){
        flag({
            type: 'error',
            title: 'POI Forms',
            body: message,
            close: 'auto'
        });
    };
    o.notifyInfo = function(message){
        flag({
            type: 'info',
            title: 'POI Forms',
            body: message,
            close: 'auto'
        });
    };
    o.copyTo = function(other) {
        for (var key in this) {
            other[key] = this[key];
        }
    };

    function createAutocomplete(client,td,editor,name){
        if (name in client.autocomplete){
            var ul = $('<ul class="poi-autocomplete">').appendTo($(document.body));
            ul.css('position', 'absolute');
            cloneProperties.forEach(function (prop) {
                ul.css(td.css(prop));
            });
            ul
                .offset({top: editor.offset().top + editor.height() + 8, left: editor.offset().left - 4})
                .css({'min-width': (editor.width() + 10) + 'px'})
                .hide()
                .click(function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    editor.val($(e.target).text());
                    editor.focus();
                    ul.hide();
                    editor.removeClass('in-poi-autocomplete-list');
                })
                .mousedown(function(){
                    editor.addClass('in-poi-autocomplete-list');
                })
                .mouseover(function(e){
                    editor.addClass('in-poi-autocomplete-list');
                    $(e.target).focus();
                })
                .mouseout(function(e){
                    editor.focus();
                    editor.removeClass('in-poi-autocomplete-list');
                })
                .keydown(function(e){
                    switch(e.which) {
                        case 37:/*ARROW_LEFT*/
                        case 38:/*ARROW_UP*/
                            e.preventDefault();
                            e.stopPropagation();
                            ul.find('li:focus').prev('li').focus();
                            break;
                        case 39:/*ARROW_RIGHT*/
                        case 40:/*ARROW_DOWN*/
                            e.preventDefault();
                            e.stopPropagation();
                            ul.find('li:focus').next('li').focus();
                            break;
                        case 9:/*TAB*/
                            editor.val($(e.target).text());
                            editor.focus();
                            ul.hide();
                            editor.removeClass('in-poi-autocomplete-list');
                            td.focus();
                            break;
                    }
                })
                .keyup(function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    switch(e.which) {
                        case 13:/*ENTER*/
                            editor.val($(e.target).text());
                            editor.focus();
                            ul.hide();
                            editor.removeClass('in-poi-autocomplete-list');
                            td.focus();
                            break;
                        case 27:/*ESC*/
                            editor.focus();
                            ul.hide();
                            editor.removeClass('in-poi-autocomplete-list');
                            editor.addClass('cancel');
                            td.focus();
                            break;
                    }
                });
            editor[0].__autocomplete = {
                list: ul,
                engine: client.autocomplete[name]
            };
        } else {
            if (name.substring(0, 11) === 'javascript:'){
                if (name.substring(11) in client.script){
                    client.autocomplete[name] = {
                        remote: client.script[name.substring(11)]
                    };
                    createAutocomplete(client,td,editor,name);
                }
            } else {
                $.ajax({
                    url: AJS.contextPath() + '/rest/poi-forms/1.0/refdata/' + client.pageId + '/' + client.file,
                    data: {
                        name: name
                    },
                    type: 'GET',
                    dataType: 'json'
                }).done(function(data){
                    client.autocomplete[name] = {
                        local: data
                    };
                    createAutocomplete(client,td,editor,name);
                }).fail(function(jqxhr){
                    console.log('poi-forms', jqxhr.responseText);
                });
            }
        }
    };

    o.PoiClient = function(pageId,file,divSheets,divTable,protection){
        var client = {
            pageId: pageId,
            file: file,
            divSheets: divSheets, // List of sheets
            divTable: divTable,   // Sheet table
            protection: protection,
            locale: window.navigator.userLanguage || window.navigator.language,
            script: require('poi-form/' + pageId + '/' + file),
            autocomplete: {}
        };
        if (client.script.onInit && typeof client.script.onInit === 'function'){
            client.script.onInit();
        }

        o.copyTo(client);
        client.openLocal = function(endpointBaseHttp){
            client.createHttpTransport(endpointBaseHttp);
            client.getWorkbook();
        };
        client.openRemote = function(endpoint,token){
            client.createWebSocketTransport(endpoint,token);
            client.getWorkbook();
        };
        client.createHttpTransport = function(endpointBaseHttp){
            client.getWorkbook = function(){
                $.ajax({
                    url: endpointBaseHttp + '/workbook/' + client.pageId,
                    data: {
                        file: client.file
                    },
                    dataType: 'json',
                    headers: { 'X-POI-FORMS-PAGEID': client.pageId }
                }).done(function(data){
                    //client.__debug(data);
                    client.version = data.version;
                    client.drawSheets(data.details);
                    client.onSheetsLoaded(data.details);
                }).fail(function(jqxhr){
                    client.notifyError(jqxhr.responseText);
                });
            };
            client.getSheet = function(sheetId){
                $.ajax({
                    url: endpointBaseHttp + '/sheet/' + client.pageId,
                    dataType: 'json',
                    type: 'GET',
                    data: {
                        file: client.file,
                        'sheet-id': sheetId
                    },
                    headers: { 'X-POI-FORMS-PAGEID': client.pageId }
                }).done(function(data){
                    //client.__debug(data);
                    client.version = data.version;
                    client.drawTable(data.details);
                }).fail(function(jqxhr){
                    client.notifyError(jqxhr.responseText);
                });
            };
            client.putCellValue = function(newValue){
                if (Object.prototype.toString.call(newValue) === '[object Array]'){
                    newValue.forEach(function(v){
                        v.file = client.file;
                        v.version = client.version;
                    });
                } else {
                    newValue.file = client.file;
                    newValue.version = client.version;
                }
                $.ajax({
                    url: endpointBaseHttp + '/value/' + client.pageId,
                    dataType: 'json',
                    type: 'PUT',
                    contentType: 'application/json',
                    data: JSON.stringify(newValue),
                    processData: false,
                    headers: { 'X-POI-FORMS-PAGEID': client.pageId }
                }).done(function(data){
                    //client.__debug(data);
                    client.version = data.version;
                    client.updateSheet(data.changes);
                }).fail(function(jqxhr){
                    switch (jqxhr.status){
                        case 409:
                            client.notifyInfo(jqxhr.responseText);
                            client.getSheet(client.selectedSheet.sheetId);
                            break;
                        default:
                            client.notifyError(jqxhr.responseText);
                    }
                });
            };
            client.addRows = function(newValue){
                newValue.file = client.file;
                newValue.version = client.version;
                $.ajax({
                    url: endpointBaseHttp + '/addrows/' + client.pageId,
                    dataType: 'json',
                    type: 'PUT',
                    contentType: 'application/json',
                    data: JSON.stringify(newValue),
                    processData: false,
                    headers: { 'X-POI-FORMS-PAGEID': client.pageId }
                }).done(function(data){
                    //client.__debug(data);
                    client.version = data.version;
                    if (client.selectedSheet.sheetId === data.changes['sheet-id']) {
                        client.getSheet(client.selectedSheet.sheetId);
                    } else {
                        client.updateSheet(data.changes);
                    }
                }).fail(function(jqxhr){
                    switch (jqxhr.status){
                        case 409:
                            client.notifyInfo(jqxhr.responseText);
                            client.getSheet(client.selectedSheet.sheetId);
                            break;
                        default:
                            client.notifyError(jqxhr.responseText);
                    }
                });
            };
            client.getChanges = function(){
                $.ajax({
                    url: endpointBaseHttp + '/changes/' + client.pageId,
                    dataType: 'json',
                    type: 'GET',
                    contentType: 'application/json',
                    data: {
                        file: client.file,
                        version: client.version
                    },
                    headers: { 'X-POI-FORMS-PAGEID': client.pageId }
                }).done(function(data){
                    //client.__debug(data);
                    client.version = data.version;
                    data.changes.forEach(function(change){
                        switch(change.changeType){
                            case "PutValue":
                                client.updateSheet(change.details);
                                break;
                            case "AddRows":
                                if (client.selectedSheet.sheetId === change.details['sheet-id']) {
                                    client.getSheet(client.selectedSheet.sheetId);
                                } else {
                                    client.updateSheet(change.details);
                                }
                                break;
                        }
                    });
                    setTimeout(client.getChanges, client.pollingInterval);
                }).fail(function(jqxhr){
                    switch (jqxhr.status){
                        case 409:
                            client.notifyInfo(jqxhr.responseText);
                            client.getSheet(client.selectedSheet.sheetId);
                            break;
                        case 410:
                            client.__debug(jqxhr.responseText);
                            break;
                        default:
                            console.log('poi-forms', jqxhr.responseText);
                    }
                    setTimeout(client.getChanges, client.pollingInterval);
                });
            };
            setTimeout(client.getChanges, 10000 + client.pollingInterval);
        };
        client.createWebSocketTransport = function(endpoint, token){
            client.__endpoint = endpoint;
            client.__token = token;
            client.__webSocket = new WebSocket(client.__endpoint);
            client.__debug(client.__endpoint);
            client.__webSocket.onopen = client.onWebSocketOpen;
            client.__webSocket.onclose = client.onWebSocketClose;
            client.__webSocket.onerror = client.onWebSocketError;
            client.__webSocket.onmessage = function (message) {
                client.onWebSocketMessage(message);
            };
            client.getWorkbook = function(retry) {
                if (client.__webSocket.readyState === 1){
                    client.__webSocket.send(JSON.stringify({"get-workbook": client.__token}));
                } else { //if (client.__webSocket.readyState === 0/*CONNECTING*/){
                    if (typeof retry === 'undefined') {
                        setTimeout(function(){
                            client.getWorkbook(1);
                        }, 100);
                    } else if (retry > 10){
                        client.notifyError('Timeout trying to connect to remote server using websocket');
                    } else {
                        setTimeout(function(){
                            client.getWorkbook(retry + 1);
                        }, 100 * retry);
                    }
                //} else {
                //    client.__debug(['WebSocket connection state', client.__webSocket.readyState]);
                }
            };
            client.getSheet = function(sheetId){
                client.__webSocket.send(JSON.stringify({"get-sheet": sheetId}));
            };
            client.putCellValue = function(newValue){
                newValue['put-value'] = true;
                client.__debug(['put-value',newValue]);
                client.__webSocket.send(JSON.stringify(newValue));
            };
            client.addRows = function(newValue){
                client.__webSocket.send(JSON.stringify({
                    'add-rows': newValue.value,
                    'sheet-id': newValue['sheet-id'],
                    'before':   newValue.point
                }));
            };
            client.onWebSocketOpen = function(e){
                client.__debug(['onWebSocketOpen', e]);
            };
            client.onWebSocketClose = function(e){
                client.__debug(['onWebSocketClose', e]);
            };
            client.onWebSocketError = function(e){
                client.__debug(['onWebSocketError', e]);
            };
            client.onWebSocketMessage = function(message){
                var data = JSON.parse(message.data);
                client.__debug(data);
                if (data.result === 'get-workbook-response'){
                    client.drawSheets(data);
                    client.onSheetsLoaded(data);
                } else if (data.result === 'get-sheet-response') {
                    client.drawTable(data);
                } else if (data.result === 'put-value-response') {
                    client.updateSheet(data);
                } else if (data.result === 'add-rows-ok') {
                    if (client.selectedSheet.sheetId === data['sheet-id']) {
                        client.getSheet(client.selectedSheet.sheetId); // redraw
                    } else {
                        client.updateSheet(data);
                    }
                } else if (data.result === 'file-saved') {
                    client.__debug('File saved');
                } else if (data.result === 'error') {
                    client.notifyError(data.errmsg);
                }
            };
        };
        client.drawSheets = function (data) {
            var $ul = $(client.divSheets).html('<div class="aui-tabs horizontal-tabs">'
                    + '<ul class="tabs-menu"></ul>'
                    + '</div>').find('ul');
            $ul.empty();
            for (var key in data.sheets) {
                var $a = $ul.append('<li class="menu-item"><a href="#" sheet-id="' + key + '"></a></li>')
                        .find('a').last();
                $a.text(data.sheets[key]);
                $a.click(function (e) {
                    e.preventDefault();
                    $(e.target).parent().parent().find('li').removeClass('active active-tab');
                    $(e.target).parent().addClass('active active-tab');
                    client.getSheet($(e.target).attr('sheet-id'));
                });
            }
            client.styles = {};
            data.styles.forEach(function (style) {
                client.styles[style.index] = style;
            });

            // Show first sheet
            $ul.find('li:first a').each(function(){
                var $a = $(this);
                $a.parent().addClass('active active-tab');
                client.getSheet($a.attr('sheet-id'));
            });
        };
        client.drawTable = function (data) {
            //console.log('poi-forms', data);
            client.selectedSheet = {};
            client.selectedSheet.sheetId = data['sheet-id'];
            client.selectedSheet.rows = {};

            var table = $(client.divTable)
                    .html('<table class="poi-table" cellspacing="0"></table>')
                    .find('table')
                    .last();

            if (data.rows) {
                data.rows.forEach(function (row) {
                    var cells = row.cells;
                    if (cells.length > 0) {
                        client.selectedSheet.rows[cells[0].point[0]] = {};
                        var tr = table
                                .append('<tr style="height: ' + Math.max(row.height, 20) + 'pt;"></tr>')
                                .find('tr')
                                .last();
                        tr.attr('poi:index', cells[0].point[0]);
                        if (row.hidden) {
                            tr.hide();
                        }
                        if (cells[0].point[1] > 0) {
                            if (cells[0].point[1] > 1) {
                                tr.append('<td colspan="' + (cells[0].point[1]) + '" style="border:0"></td>');
                            } else {
                                tr.append('<td style="border:0"></td>');
                            }
                        }
                        for (var i = 0; i < cells.length; i++) {
                            if (i + 1 < cells.length && cells[i + 1].point[1] - cells[i].point[1] > 1) {
                                tr.append('<td colspan="' + (cells[i + 1].point[1] - cells[i].point[1]) + '"></td>');
                            } else {
                                tr.append('<td></td>');
                            }
                            var cell = cells[i];
                            cell.id = o.uniqueId();
                            cell.client = client;
                            cell.sheetId = cell.sheetId | data['sheet-id'];
                            var td = tr.find('td').last();
                            td.attr('id', cell.id);
                            td.attr('poi:index', cell.point);
                            td[0].__poiCell = cell;
                            client.selectedSheet.rows[cell.point[0]][cell.point[1]] = cell;
                            if (cell.span && cell.span[0] > 1) {
                                td.attr('rowspan', cell.span[0]);
                            }
                            if (cell.span && cell.span[1] > 1) {
                                td.attr('colspan', cell.span[1]);
                            }
                            client.printCellValue(cell, td);
                        }
                    }
                });
            }

            if (data.rowHeight) {
            }

            table
                .on('click dblclick', function(e){
                    client.showEditor(e);
                })
                .keydown(function(e){
                    if (e.which === 38/*ARROW_UP*/) {
                        client.cellUp($(e.target), function(){
                            e.preventDefault();
                            e.stopPropagation();
                        });
                    } else if (e.which === 40/*ARROW_DOWN*/) {
                        client.cellDown($(e.target), function(){
                            e.preventDefault();
                            e.stopPropagation();
                        });
                    } else if (
                        (e.which > 47  && e.which < 58)   || // number keys
                        e.which === 32                    || // space
                        (e.which > 64  && e.which < 91)   || // letter keys
                        (e.which > 95  && e.which < 112)  || // numpad keys
                        (e.which > 185 && e.which < 193)  || // ;=,-./` (in order)
                        (e.which > 218 && e.which < 223)     // [\]' (in order)
                    ){
                        client.showEditor(e,true/*nocopy*/);
                    }
                })
                .keyup(function(e){
                    if (e.which === 37/*ARROW_LEFT*/) {
                        $(e.target).prevAll('[tabindex]:first').focus();
                    } else if (e.which === 39/*ARROW_RIGHT*/) {
                        $(e.target).nextAll('[tabindex]:first').focus();
                    } else if (e.which === 46)/*DELETE*/ {
                        client.clearCellText($(e.target));
                        e.preventDefault();
                        e.stopPropagation();
                    }
                });

            return table;
        };
        client.cellUp = function(td,callback) {
            var tr = td.parent();
            var col = td[0].__poiCell.point[1];
            for (var keepgoing = true; keepgoing;) {
                tr = tr.prev();
                if (tr.length === 0) {
                    break;
                } else if (!tr.is(':visible')) {
                    continue;
                } else {
                    tr.children('[tabindex]').each(function(){
                        if (this.__poiCell.point[1] === col) {
                            keepgoing = false;
                            if (typeof callback === 'function') {
                                callback();
                            }
                            $(this).focus();
                        }
                    });
                }
            }
        };
        client.cellDown = function(td,callback) {
            var tr = td.parent();
            var col = td[0].__poiCell.point[1];
            for (var keepgoing = true; keepgoing;) {
                tr = tr.next();
                if (tr.length === 0) {
                    break;
                } else if (!tr.is(':visible')) {
                    continue;
                } else {
                    tr.children('[tabindex]').each(function(){
                        if (this.__poiCell.point[1] === col) {
                            keepgoing = false;
                            if (typeof callback === 'function') {
                                callback();
                            }
                            $(this).focus();
                        }
                    });
                }
            }
        };
        client.flicker = function(cells,n){
            if (n === 0){
                cells.forEach(function($cell){
                    $cell.css('opacity', 'inherit');
                });
            } else {
                cells.forEach(function($cell){
                    $cell.css('opacity', flickering[n % 10]);
                });
                setTimeout(client.flicker, 100, cells, n - 1);
            }
        };
        client.updateSheet = function (data) {
            client.unhighlight();
            var modifiedCells = [];
            data.cells.forEach(function (cellValue) {
                client.highlight(cellValue.sheetId);
                if (client.selectedSheet && client.selectedSheet.sheetId === cellValue['sheet-id']) {
                    var row = client.selectedSheet.rows[cellValue.point[0]];
                    var cell = row[cellValue.point[1]];
                    if (typeof cell === 'undefined') {
                        o.__debug(['Invalid cell value', cellValue]);
                    } else {
                        modifiedCells.push(client.updateCell(cell, cellValue));
                    }
                }
            });
            if (client.flickering){
                client.flicker(modifiedCells,30);
            }
        };
        client.updateCell = function (cell, cellValue) {
            // Unset old values
            if ('string-value' in cell) {
                delete cell['string-value'];
            }
            if ('boolean-value' in cell) {
                delete cell['boolean-value'];
            }
            if ('number-value' in cell) {
                delete cell['number-value'];
            }
            if ('date-value' in cell) {
                delete cell['date-value'];
            }

            // Set to new values
            if ('string-value' in cellValue) {
                cell['string-value'] = cellValue['string-value'];
            } else if ('boolean-value' in cellValue) {
                cell['boolean-value'] = cellValue['boolean-value'];
            } else if ('number-value' in cellValue) {
                cell['number-value'] = cellValue['number-value'];
            } else if ('date-value' in cellValue) {
                cell['date-value'] = cellValue['date-value'];
            }

            var $td = $('#' + cell.id);
            client.printCellValue(cell, $td);
            if (typeof client.script.onChange === 'function'){
                client.script.onChange(cell, $('#' + cell.id));
            }
            return $td;
        };
        client.printCellValue = function (cell, td) {
            if (td.length === 0) {
                return;
            }
            var style = this.styles[cell.style];

            if ('string-value' in cell) {
                td.text(cell['string-value']);
            } else if ('boolean-value' in cell) {
                td.text(cell['boolean-value']);
            } else if ('number-value' in cell) {
                if ('format' in style){
                    try {
                        td.text(ssf.format(style.format,cell['number-value']));
                    } catch (ignore) {
                        //console.log('poi-forms', 'Format failed: ' + style.format, cell['number-value'], ignore);
                        td.text(cell['number-value']);
                    }
                } else {
                    td.text(cell['number-value']);
                }
                td.css('text-align', 'right');
            } else if ('date-value' in cell) {
                try {
                    td.text(moment(cell['date-value']).format('L'));
                } catch (e) {
                    o.__debug(e);
                    td.text(cell['date-value']);
                }
            } else {
                td.empty();
            }

            if (style.color) {
                td.css('color', style.color);
            }
            if (style.bgcolor !== '') {
                td.css('background-color', style.bgcolor);
            }
            if (style.border[0] === 0) {
                td.css('border-left', '0');
            }
            if (style.border[1] === 0) {
                td.css('border-right', '0');
            }
            if (style.border[2] === 0) {
                td.css('border-top', '0');
            }
            if (style.border[3] === 0) {
                td.css('border-bottom', '0');
            }
            if (style.align) {
                td.css('text-align', style.align);
            }
            if (style.font) {
                td.css('font', style.font);
            }
            if (cell.link) {
                if (/^\?AutoComplete\((.+)\)$/g.exec(cell.link) !== null || cell.link === '?Placeholder') {
                    td.attr('placeholder', cell.linkLabel);
                } else {
                    var text = td.text();
                    var a = td.html('<a href="#"></a>').find('a');
                    a.text(text === ''? cell.linkLabel: text);
                    a.attr('href', cell.link);
                    a.click(function (e) {
                        e.preventDefault();
                        e.stopPropagation();
                        client.onlink(e.target);
                    });
                }
            }
            if (client.protection && style.locked) {
                td.css('cursor', 'default');
            } else {
                td.css('cursor', 'text').prop('tabindex', 1);
            }
        };
        client.unhighlight = function () {
            /* TODO */
        };
        client.highlight = function (sheetId) {
            /* TODO */
        };
        client.onlink = function (a) {
            var href = $(a).attr('href');
            var match = /^\?AddRows\((.+)\)$/g.exec(href);
            if (match !== null) {
                client.addRows({
                    'sheet-id': a.parentElement.__poiCell.sheetId,
                    'before':   a.parentElement.__poiCell.point,
                    'add-rows': match[1]
                });
            }
        };
        client.showEditor = function (e,nocopy) {
            var td = $(e.target); //.find('td:focus');
            var pc = td[0].__poiCell;
            if (client.protection && pc.client.styles[pc.style].locked) {
                return;
            }

            var editor = $('<input>').appendTo($(document.body));
            editor.css('position', 'absolute');
            cloneProperties.forEach(function (prop) {
                editor.css(td.css(prop));
            });
            if (typeof nocopy === 'undefined' || !nocopy) {
                if ('number-value' in pc) {
                    editor.val(pc['number-value']);
                } else {
                    editor.val(td.text());
                }
            }
            editor
                    .offset(td.offset())
                    .width(td.width())
                    .height(td.height())
                    .show()
                    .focus();
            editor.blur(function (e) {
                e.preventDefault();
                e.stopPropagation();
                if ($(e.target).hasClass('in-poi-autocomplete-list')) {
                    return;
                } else {
                    if (!$(e.target).hasClass('cancel')) {
                        client.setCellText(editor, td);
                    }
                    editor.remove();
                    if (editor[0].__autocomplete) {
                        editor[0].__autocomplete.list.remove();
                    }
                    e.preventDefault();
                    e.stopPropagation();
                }
            });

            editor.keydown(function (e) {
                switch (e.which) {
                    case 13:/*ENTER*/
                        //console.log('Enter');
                        td.focus();
                        e.preventDefault();
                        e.stopPropagation();
                        break;
                    case 27:/*ESC*/
                        //console.log('Esc');
                        $(e.target).addClass('cancel');
                        td.focus();
                        e.preventDefault();
                        e.stopPropagation();
                        break;
                    case 9:/*TAB*/
                        td.focus();
                        break;
                    case 37:/*ARROW_LEFT*/
                        break;
                    case 39:/*ARROW_RIGHT*/
                        break;
                    case 38:/*ARROW_UP*/
                        client.cellUp(td, function(){
                            e.preventDefault();
                            e.stopPropagation();
                        });
                        break;
                    case 40:/*ARROW_DOWN*/
                        if (e.target.__autocomplete && e.target.__autocomplete.list.is(':visible') && e.target.__autocomplete.list.children().length > 0) {
                            $(e.target).addClass('in-poi-autocomplete-list');
                            e.target.__autocomplete.list.children().first().focus();
                        } else {
                            client.cellDown(td, function(){
                                e.preventDefault();
                                e.stopPropagation();
                            });
                        }
                        break;
                }
            });

            /*
             * Autocomplete support
             * 
             * If source cell has a link with reference to #?AutoComplete(<group>)
             * it will be rendered with a selection menu
             */
            if (pc.link) {
                var match = /^\?AutoComplete\((.+)\)$/g.exec(pc.link);
                if (match !== null) {
                    createAutocomplete(client,td,editor,match[1]);
                }
            }

            editor.keyup(function(e){
                if (e.target.__autocomplete) {
                    var editor = $(e.target);
                    var val = editor.val();
                    var oldVal = e.target.__oldVal;
                    if (val === oldVal) {
                        return;
                    } else {
                        e.target.__oldVal = val;
                    }
                    o.autocomplete.filter(e.target.__autocomplete.engine, val, e.target.__autocomplete.list);
/*
                    var autocomplete = e.target.__autocomplete;
                    if (val.length > 0) {
                        autocomplete.list.empty();
                        autocomplete.promise.then(function() {
                            autocomplete.engine.search(val, function(strings) {
                                strings.forEach(function(string){
                                    $('<li>').text(string).prop('tabindex', 1).appendTo(autocomplete.list);
                                });
                            });
                        });
                        autocomplete.list.show();
                    } else {
                        autocomplete.list.hide();
                    }
*/                }
            });

        };
        client.setCellText = function (editor, td) {
            var text = editor.val();
            var cell = td[0].__poiCell;

            var dateValue = moment(text, 'L', true);
            if (dateValue.isValid()) {
                if (!('date-value' in cell) || dateValue.valueOf() !== cell['date-value']) {
                    cell.client.putCellValue({
                        'sheet-id':   cell.sheetId,
                        'point':      cell.point,
                        'date-value': dateValue.valueOf()
                    });
                }
                return;
            }
            var numberValue = new Number(text);
            if (!isNaN(numberValue)) {
                if (!('number-value' in cell) || numberValue !== cell['number-value']) {
                    cell.client.putCellValue({
                        'sheet-id':     cell.sheetId,
                        'point':        cell.point,
                        'number-value': numberValue
                    });
                }
                return;
            }
            if (text.toLowerCase() === "true") {
                if (!('boolean-value' in cell) || !cell['boolean-value']) {
                    cell.client.putCellValue({
                        'sheet-id':      cell.sheetId,
                        'point':         cell.point,
                        'boolean-value': true
                    });
                }
                return;
            }
            if (text.toLowerCase() === "false") {
                if (!('boolean-value' in cell) || cell['boolean-value']) {
                    cell.client.putCellValue({
                        'sheet-id':      cell.sheetId,
                        'point':         cell.point,
                        'boolean-value': false
                    });
                }
                return;
            }
            if (!('string-value' in cell) || text !== cell['string-value']) {
                cell.client.putCellValue({
                    'sheet-id':     cell.sheetId,
                    'point':        cell.point,
                    'string-value': text
                });
            }
        };
        client.clearCellText = function(td) {
            var pc = td[0].__poiCell;
            var newValue = {
                'sheet-id': pc.sheetId,
                point: pc.point,
                value: null
            };
            pc.client.putCellValue(newValue);
        };
        client.onSheetsLoaded = function(data){
            return true;
        };
        return client;
    };
    return o;
});