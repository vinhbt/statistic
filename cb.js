var SD = {
    //bucket fodi: no view
    fodi: {},
    log: {
        free_coin: {},
        tax: {},
        tax_range: {},
        pay: {},
        pay_range: {}
    },
    acc: {

    }
};

/**
 * Tính số tiền nạp trong 1 ngày
 */

SD.log.pay = function (doc, meta){

    //id: l{app}{b36(created)}_{b36(random)}
    if(meta.id.charAt(0) != 'l') return;
    var i = meta.id.indexOf('_'),
        app = parseInt(meta.id.charAt(1)),
        created = parseInt(meta.id.substring(2, i), 36),
        d = new Date(created * 1000),
        key = d.getMonth() + "_" + d.getDate();
    if (app == 0 && doc.m.indexOf("Nạp Bảo") == 0){
        var str = doc.m.replace("VNĐ", "").trim();
        var n = str.lastIndexOf(" ");
        var num = parseInt(str.substring(n + 1));
        var paidBy = 0; //nạp thẻ
        if (str.indexOf("SMS") > 0){
            paidBy = 1;
        }else if (str.indexOf("tiền mặt") > 0){
            paidBy = 2;
        }else if (str.indexOf("Google Wallet") > 0){
            paidBy = 3;
        }else if (str.indexOf("Apple Wallet") > 0){
            paidBy = 4;
        }
        emit(key, {b: paidBy, n: num, c: doc.d[0][1]} );
    }else if (app == 0 && doc.m.indexOf("Tặng Bảo miễn phí lần") >= 0){ // 1, 2, 3, 4, 5.
        var num1 = parseInt(doc.m.replace("Tặng Bảo miễn phí lần ",""));
        emit(key, {b: 4 + num1, n: 1, c: doc.d[0][1]});
    }
};


SD.log.pay.reduce = function (keys, values, rereduce) {
    //              thẻ cào, SMS, tiền mặt, google wallet, apple wallet, mp1, mp2, mp3, mp4, mp5, số bảo.
    var outRange = [0,         0,        0,             0,            0,   0,   0,   0,   0,   0,     0];

    if (!rereduce){ // map function
        for (i in values) {
            outRange[values[i].b] += values[i].n;
            outRange[10] += values[i].c;
        }
    }else{
        for (i in values) {
            for (k in values[i]){
                outRange[k] += values[i][k];
            }
        }
    }
    return outRange;
};

SD.log.pay_range = function (doc, meta){

    //id: l{app}{b36(created)}_{b36(random)}
    if(meta.id.charAt(0) != 'l') return;
    var i = meta.id.indexOf('_'),
        app = parseInt(meta.id.charAt(1)),
        created = parseInt(meta.id.substring(2, i), 36),
        month = new Date(created * 1000).getMonth();

    if (app == 0 && doc.m.indexOf("Nạp Bảo") == 0){
        var str = doc.m.replace("VNĐ", "").trim();
        var n = str.lastIndexOf(" ");
        var num = parseInt(str.substring(n + 1));

        emit([month, doc.d[0][0]], {n: num, c: doc.d[0][1]});
    }
};

SD.log.pay_range.reduce = function (keys, values, rereduce) {
    var out = {n: 0, c: 0};
    for (i in values) {
        out.n += values[i].n;
        out.c += values[i].c;
    }
    return out;
};
/**b
 * Tính số bảo free trong 1 khoảng thời gian
 */
SD.log.free_coin= function (doc, meta){

    //id: l{app}{b36(created)}_{b36(random)}
    if(meta.id.charAt(0) != 'l') return;
    var i = meta.id.indexOf('_'),
        app = parseInt(meta.id.charAt(1)),
        created = parseInt(meta.id.substring(2, i), 36);
    if (app == 0 && doc.m.indexOf("Tặng Bảo miễn phí lần") >= 0){
        var num = parseInt(doc.m.replace("Tặng Bảo miễn phí lần ",""));
        emit(created, num - 1);
    }
};

SD.log.free_coin.reduce = function (keys, values, rereduce) {
    // 1 lần, 2 lần, 3 lần, 4 lần, 5 lần.
    var outRange = [0, 0 , 0, 0, 0];

    if (!rereduce){ // map function
        for (i in values) {
            outRange[values[i]] += 1;
        }
    }else{
        for (i in values) {
            for (k in values[i]){
                outRange[k] += values[i][k];
            }
        }
    }
    return outRange;

};

// n1 query select count(*) from log where META(log).id like 'l%' and ARRAY_LENGTH(d)=1 AND d[0][1]=50000;

//select d[0][0] from log where META(log).id like 'l0%'
// and ARRAY_LENGTH(d)=1 AND d[0][1]=50000 group by d[0][0] having count(*) > 4;
// lấy metrics.resultCount.
/**
 * Tính số bảo phế kiếm được trong 1 khoảng thời gian,
 * Tính tổng số tiền nhận được và mất trong một ván rồi dùng reduce sum.
 */
SD.log.tax = function (doc, meta){

    //id: l{app}{b36(created)}_{b36(random)}
    if(meta.id.charAt(0) != 'l') return;
    var i = meta.id.indexOf('_'),
        app = parseInt(meta.id.charAt(1)),
        created = parseInt(meta.id.substring(2, i), 36);
    if (app > 0){
        var sum = 0;
        doc.d.forEach(function(d){
            sum += d[1];
        });
        emit(created, sum);
    }
};
SD.log.tax.reduce = _sum;

//n1 query select sum(e) from log unnest log.d as d unnest d[1] as e where META(log).id like 'l%' and ARRAY_LENGTH(log.d) > 1 ;

SD.log.tax_range = function (doc, meta) {
    if(meta.id.charAt(0) != 'l') return;
    var i = meta.id.indexOf('_'),
        app = parseInt(meta.id.charAt(1)),
        created = parseInt(meta.id.substring(2, i), 36);


    if (app > 0 && doc.m.search('cược') >= 0){
        var pos = doc.m.indexOf('.'),
            str1 = doc.m.substring(0, pos),
            pos1 = str1.lastIndexOf(' '),
            cuoc = parseInt(str1.substring(pos1 + 1));

        var sum = 0;
        doc.d.forEach(function(d){
            sum += d[1];
        });
        emit(created, {s: sum, a: app, c: cuoc});
    }
};

SD.log.tax_range.reduce = function (keys, values, rereduce) {
    // <=100k, <500k, <1M, <2M, <5M, >5M,tổng số ván chắn, tổng số ván phỏm, tổng số phế chắn thu được, tổng số phế phỏm thu được.
    var outRange = [0, 0 , 0, 0, 0, 0, 0, 0, 0 , 0];

    if (!rereduce){ // map function
        for (i in values) {
            var range = 0;
            if (values[i].c <= 100000){
                range = 0;
            }else if (values[i].c <= 500000){
                range = 1;
            }else if (values[i].c <= 1000000){
                range = 2;
            }else if (values[i].c <= 2000000){
                range = 3;
            }else if (values[i].c <= 5000000){
                range = 4;
            }else {
                range = 5;
            }
            outRange[range] += values[i].s;
            outRange[values[i].a + 5] += 1;
            outRange[values[i].a + 7] += values[i].s;
        }
    }else{
        for (i in values) {
            for (k in values[i]){
                outRange[k] += values[i][k];
            }
        }
    }
    return outRange;
};


function (doc, meta) {
    if (meta.id.charAt(0) != 'a' || meta.id.charAt(1) != '2') return;
    var i = meta.id.length;
    var uid = parseInt(meta.id.substring(2, i), 36);
    emit(uid, {t: doc.t, w: doc.w});
}

function (keys, values, rereduce) {
    var out = {u1: 0, u2: 0, t: 0, w: 0};
    if (!rereduce) {
        for (i in values) {
            if (values[i].t > out.t) {
                out.t = values[i].t;
                out.u1 = keys[i];
            }
            if (values[i].w > out.w) {
                out.w = values[i].w;
                out.u2 = keys[i];
            }
        }
    } else {
        for (i in values) {
            if (values[i].t > out.t) {
                out.t = values[i].t;
                out.u1 = values[i].u1;
            }
            if (values[i].w > out.w) {
                out.w = values[i].w;
                out.u2 = values[i].u2;
            }
        }
    }

    return out;
}
//31-7-2015 11h39p18 GMT+7 = 1438317558
//truoc 5 phut = 1438317258
SD.log.chiaga = function(doc, meta){
    if (meta.id.charAt(0) != 'r'){
        var i = meta.id.indexOf('_'),
            app = parseInt(meta.id.charAt(1)),
            created = parseInt(meta.id.substring(2, i), 36);
        if (app == 1 && created > 1438317258 && created < 1438317558) {//chan
            emit(null)
        }
    }
};

SD.acc.thicu = function(doc, meta){
    if(meta.id.charAt(0) != 'j') return;
    var uid = parseInt(meta.id.substring(2, meta.id.length), 36);
    emit(uid, null);
};

