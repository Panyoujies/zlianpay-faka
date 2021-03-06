package cn.zlianpay.reception.controller;

import cn.hutool.crypto.SecureUtil;
import cn.zlianpay.carmi.entity.Cards;
import cn.zlianpay.carmi.service.CardsService;
import cn.zlianpay.common.core.pays.payjs.SignUtil;
import cn.zlianpay.common.core.pays.paypal.PaypalSend;
import cn.zlianpay.common.core.pays.xunhupay.PayUtils;
import cn.zlianpay.common.core.pays.zlianpay.ZlianPay;
import cn.zlianpay.common.core.utils.DateUtil;
import cn.zlianpay.common.core.utils.FormCheckUtil;
import cn.zlianpay.common.core.web.JsonResult;
import cn.zlianpay.reception.dto.NotifyDTO;
import cn.zlianpay.reception.entity.XunhuNotIfy;
import cn.zlianpay.reception.util.SynchronizedByKeyService;
import cn.zlianpay.settings.entity.ShopSettings;
import cn.zlianpay.settings.service.ShopSettingsService;
import cn.zlianpay.website.entity.Website;
import cn.zlianpay.website.service.WebsiteService;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.zlianpay.common.core.pays.mqpay.mqPay;
import cn.zlianpay.common.core.utils.RequestParamsUtil;
import cn.zlianpay.common.core.utils.StringUtil;
import cn.zlianpay.common.system.service.EmailService;
import cn.zlianpay.orders.entity.Orders;
import cn.zlianpay.orders.service.OrdersService;
import cn.zlianpay.products.entity.Products;
import cn.zlianpay.products.service.ProductsService;
import cn.zlianpay.settings.entity.Pays;
import cn.zlianpay.settings.service.PaysService;
import com.github.wxpay.sdk.WXPayUtil;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.Transaction;
import com.paypal.base.rest.PayPalRESTException;
import com.zjiecode.wxpusher.client.WxPusher;
import com.zjiecode.wxpusher.client.bean.Message;
import org.apache.commons.codec.Charsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Controller
@Transactional
public class NotifyController {

    @Autowired
    private PaysService paysService;

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private ProductsService productsService;

    @Autowired
    private CardsService cardsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private WebsiteService websiteService;

    @Autowired
    private ShopSettingsService shopSettingsService;

    @Autowired
    private SynchronizedByKeyService synchronizedByKeyService;

    /**
     * ????????????xml
     */
    private String WxpayresXml = "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";

    private String WxpayH5resXml = "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";

    private String resFailXml = "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[????????????]]></return_msg></xml>";

    @RequestMapping("/mqpay/notifyUrl")
    @ResponseBody
    public String notifyUrl(HttpServletRequest request) {
        Map<String, String> params = RequestParamsUtil.getParameterMap(request);
        String param = params.get("param");
        String price = params.get("price");
        String money = params.get("reallyPrice");
        String sign = params.get("sign");
        String payId = params.get("payId");
        String type = params.get("type");
        String key = null;
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", payId));
        if (Integer.parseInt(type) == 1) { // wxpay
            Pays wxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "mqpay_wxpay"));

            /**
             * ????????????
             */
            if (!orders.getPayType().equals(wxPays.getDriver())) {
                return "?????????????????????";
            }

            Map mapTypes = JSON.parseObject(wxPays.getConfig());
            key = mapTypes.get("key").toString();
        } else if (Integer.parseInt(type) == 2) { // alipay
            Pays aliPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "mqpay_alipay"));

            /**
             * ????????????
             */
            if (!orders.getPayType().equals(aliPays.getDriver())) {
                return "?????????????????????";
            }

            Map mapTypes = JSON.parseObject(aliPays.getConfig());
            key = mapTypes.get("key").toString();
        }

        String mysign = mqPay.md5(payId + param + type + price + money + key);

        if (mysign.equals(sign)) {
            String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String seconds = new SimpleDateFormat("HHmmss").format(new Date());
            String number = StringUtil.getRandomNumber(6);
            String payNo = date + seconds + number;

            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(payId, () -> {
                String returnBig1 = returnBig(money, price, payId, payNo, param, "success", "fiald");
                notifyText.set(returnBig1);
            });
            return notifyText.get();

        } else {
            return "fiald";
        }
    }

    @RequestMapping("/mqpay/returnUrl")
    public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {

        /**
         *???????????? ?????????????????????
         */
        Map<String, String> params = RequestParamsUtil.getParameterMap(request);
        String param = params.get("param");
        String price = params.get("price");
        String reallyPrice = params.get("reallyPrice");
        String sign = params.get("sign");
        String payId = params.get("payId");
        String type = params.get("type");

        String key = null;
        if (Integer.parseInt(type) == 1) { // wxpay
            Pays wxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "mqpay_wxpay"));
            Map mapTypes = JSON.parseObject(wxPays.getConfig());
            key = mapTypes.get("key").toString();
        } else if (Integer.parseInt(type) == 2) { // alipay
            Pays aliPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "mqpay_alipay"));
            Map mapTypes = JSON.parseObject(aliPays.getConfig());
            key = mapTypes.get("key").toString();
        }
        String mysign = mqPay.md5(payId + param + type + price + reallyPrice + key);
        if (mysign.equals(sign)) {
            String url = "/pay/state/" + payId;
            response.sendRedirect(url);
        }
    }

    @RequestMapping("/zlianpay/notifyUrl")
    @ResponseBody
    public String zlianpNotify(HttpServletRequest request) {
        Map<String, String> parameterMap = RequestParamsUtil.getParameterMap(request);

        String pid = parameterMap.get("pid");
        String type = parameterMap.get("type");
        String out_trade_no = parameterMap.get("out_trade_no");

        String driver = "";
        if (type.equals("wxpay")) {
            driver = "zlianpay_wxpay";
        } else if (type.equals("alipay")) {
            driver = "zlianpay_alipay";
        } else if (type.equals("qqpay")) {
            driver = "zlianpay_qqpay";
        }

        Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", driver));

        /**
         * ????????????
         */
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", out_trade_no));
        if (!orders.getPayType().equals(pays.getDriver())) {
            return "?????????????????????";
        }

        Map mapTypes = JSON.parseObject(pays.getConfig());

        // ??????key ???????????????
        String secret_key = mapTypes.get("key").toString();
        String trade_no = parameterMap.get("trade_no");
        String name = parameterMap.get("name");
        String money = parameterMap.get("money");
        String trade_status = parameterMap.get("trade_status");
        String return_url = parameterMap.get("return_url");
        String notify_url = parameterMap.get("notify_url");
        String sign = parameterMap.get("sign");
        String sign_type = parameterMap.get("sign_type");

        Map<String, Object> params = new HashMap<>();
        params.put("pid", pid);
        params.put("trade_no", trade_no);
        params.put("out_trade_no", out_trade_no);
        params.put("type", type);
        params.put("name", name);
        params.put("money", money);
        params.put("return_url", return_url);
        params.put("notify_url", notify_url);
        params.put("trade_status", trade_status);

        String sign1 = ZlianPay.createSign(params, secret_key);

        if (sign1.equals(sign)) {
            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(out_trade_no, () -> {
                String returnBig1 = returnBig(money, money, out_trade_no, trade_no, name, "success", "final");
                notifyText.set(returnBig1);
            });
            return notifyText.get();
        } else {
            return "??????????????????";
        }
    }

    @RequestMapping("/zlianpay/returnUrl")
    @ResponseBody
    public void zlianpReturnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {

        /**
         *???????????? ?????????????????????
         */
        Map<String, String> parameterMap = RequestParamsUtil.getParameterMap(request);

        String pid = parameterMap.get("pid");
        String type = parameterMap.get("type");

        String driver = "";
        if (type.equals("wxpay")) {
            driver = "zlianpay_wxpay";
        } else if (type.equals("alipay")) {
            driver = "zlianpay_alipay";
        } else if (type.equals("qqpay")) {
            driver = "zlianpay_qqpay";
        }

        Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", driver));
        Map mapTypes = JSON.parseObject(pays.getConfig());

        // ??????key ???????????????
        String secret_key = mapTypes.get("key").toString();
        String trade_no = parameterMap.get("trade_no");
        String out_trade_no = parameterMap.get("out_trade_no");
        String name = parameterMap.get("name");
        String money = parameterMap.get("money");
        String trade_status = parameterMap.get("trade_status");
        String return_url = parameterMap.get("return_url");
        String notify_url = parameterMap.get("notify_url");
        String sign = parameterMap.get("sign");
        String sign_type = parameterMap.get("sign_type");

        Map<String, Object> params = new HashMap<>();
        params.put("pid", pid);
        params.put("trade_no", trade_no);
        params.put("out_trade_no", out_trade_no);
        params.put("type", type);
        params.put("name", name);
        params.put("money", money);
        params.put("return_url", return_url);
        params.put("notify_url", notify_url);
        params.put("trade_status", trade_status);

        String sign1 = ZlianPay.createSign(params, secret_key);

        if (sign1.equals(sign)) {
            String url = "/pay/state/" + out_trade_no;
            response.sendRedirect(url);
        }
    }

    /**
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping("/yungouos/notify")
    public String notify(HttpServletRequest request) throws NoSuchAlgorithmException {
        Map<String, String> params = RequestParamsUtil.getParameterMap(request);
        String payNo = params.get("payNo");
        String code = params.get("code");
        String mchId = params.get("mchId");
        String orderNo = params.get("orderNo");
        String money = params.get("money");
        String outTradeNo = params.get("outTradeNo");
        String sign = params.get("sign");
        String payChannel = params.get("payChannel");
        String attach = params.get("attach");

        Map<String, String> map = new HashMap<>();
        map.put("code", code);
        map.put("orderNo", orderNo);
        map.put("outTradeNo", outTradeNo);
        map.put("payNo", payNo);
        map.put("money", money);
        map.put("mchId", mchId);

        String key = null;
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", outTradeNo));
        switch (payChannel) {
            //????????????????????????????????? ???????????????????????????????????????????????? ???????????????????????? yungouos.com-???????????????-???????????????-??????????????????
            case "wxpay":
                Pays wxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "yungouos_wxpay"));

                /**
                 * ??????????????????
                 */
                if (!orders.getPayType().equals(wxPays.getDriver())) {
                    return "?????????????????????";
                }

                Map wxMap = JSON.parseObject(wxPays.getConfig());
                key = wxMap.get("key").toString();
                break;
            case "alipay":
                Pays alipays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "yungouos_alipay"));

                /**
                 * ??????????????????
                 */
                if (!orders.getPayType().equals(alipays.getDriver())) {
                    return "?????????????????????";
                }

                Map aliMap = JSON.parseObject(alipays.getConfig());
                key = aliMap.get("key").toString();
                break;
            default:
                break;
        }

        String mySign = createSign(map, key);
        if (mySign.equals(sign) && Integer.parseInt(code) == 1) {
            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(outTradeNo, () -> {
                String returnBig1 = returnBig(money, money, outTradeNo, payNo, attach, "SUCCESS", "FIALD");
                notifyText.set(returnBig1);
            });
            return notifyText.get();
        } else {
            //????????????
            return "FIALD";
        }
    }

    /**
     * ?????????????????????
     * @return
     */
    @RequestMapping("/xunhupay/notifyUrl")
    @ResponseBody
    public String xunhuNotifyUrl(XunhuNotIfy xunhuNotIfy) {
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", xunhuNotIfy.getTrade_order_id()));
        String key = null;
        if (orders.getPayType().equals("xunhupay_wxpay")) {
            Pays xunhuwxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "xunhupay_wxpay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(xunhuwxPays.getDriver())) {
                return "?????????????????????";
            }

            Map xunhuwxMap = JSON.parseObject(xunhuwxPays.getConfig());
            key = xunhuwxMap.get("appsecret").toString();
        } else if (orders.getPayType().equals("xunhupay_alipay")) {
            Pays xunhualiPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "xunhupay_alipay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(xunhualiPays.getDriver())) {
                return "?????????????????????";
            }

            Map xunhualiMap = JSON.parseObject(xunhualiPays.getConfig());
            key = xunhualiMap.get("appsecret").toString();
        }

        Map map = JSON.parseObject(JSON.toJSONString(xunhuNotIfy), Map.class);

        String sign = PayUtils.createSign(map, key);
        if (sign.equals(xunhuNotIfy.getHash()) && "OD".equals(xunhuNotIfy.getStatus())) {
            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(xunhuNotIfy.getTrade_order_id(), () -> {
                String returnBig = returnBig(xunhuNotIfy.getTotal_fee().toString(), xunhuNotIfy.getTotal_fee().toString(), xunhuNotIfy.getTrade_order_id(), xunhuNotIfy.getTransaction_id(), xunhuNotIfy.getPlugins(), "success", "fiald");
                notifyText.set(returnBig);
            });
            return notifyText.get();
        } else {
            return "fiald";
        }
    }

    /**
     * ?????????????????????
     *
     * @param request
     * @return
     */
    @RequestMapping("/xunhupay/returnUrl")
    @ResponseBody
    public void xunhuReturnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // ?????? map ???????????????????????? ?????? ??????????????? ???[0]
        Map<String, String> params = RequestParamsUtil.getParameterMap(request);
        String url = "/pay/state/" + params.get("trade_order_id");
        response.sendRedirect(url);
    }

    private String order_id;

    /**
     * ???????????????
     *
     * @param request
     * @return
     */
    @RequestMapping("/jiepay/notifyUrl")
    @ResponseBody
    public String jiepayNotifyUrl(HttpServletRequest request) {
        // ?????? map ???????????????????????? ?????? ??????????????? ???[0]
        Map<String, String> params = RequestParamsUtil.getParameterMap(request);
        String code = params.get("code");
        String order_id = params.get("order_id");
        String order_rmb = params.get("order_rmb");
        String diy = params.get("diy");
        String sign = params.get("sign");

        String appid = "";
        String apptoken = "";
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", order_id));
        if (code.equals("1")) {
            Pays aliPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "jiepay_alipay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(aliPays.getDriver())) {
                return "?????????????????????";
            }

            Map wxMap = JSON.parseObject(aliPays.getConfig());
            appid = wxMap.get("appid").toString();
            apptoken = wxMap.get("apptoken").toString();
        } else if (code.equals("2")) {
            Pays wxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "jiepay_wxpay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(wxPays.getDriver())) {
                return "?????????????????????";
            }

            Map wxMap = JSON.parseObject(wxPays.getConfig());
            appid = wxMap.get("appid").toString();
            apptoken = wxMap.get("apptoken").toString();
        }

        String newSign = SecureUtil.md5(appid + apptoken + code + order_id + order_rmb + diy);
        if (sign.equals(newSign)) {
            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(order_id, () -> {
                String returnBig = returnBig(order_rmb, order_rmb, order_id, System.currentTimeMillis() + "", diy, "success", "fiald");
                notifyText.set(returnBig);
            });
            return notifyText.get();
        } else {
            return "error";
        }
    }

    /**
     * ???????????????
     *
     * @param request
     * @return
     */
    @RequestMapping("/jiepay/returnUrl")
    @ResponseBody
    public void jiepayReturnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        while (true) {
            if (!StringUtils.isEmpty(this.order_id)) {
                break;
            }
        }
        String url = "/pay/state/" + this.order_id;
        response.sendRedirect(url);
    }

    /**
     * ????????????
     *
     * @param notifyDTO
     * @return
     */
    @RequestMapping("/payjs/notify")
    @ResponseBody
    public Object payjsNotify(NotifyDTO notifyDTO) {
        Map<String, Object> notifyData = new HashMap<>();
        notifyData.put("return_code", notifyDTO.getReturn_code());
        notifyData.put("total_fee", notifyDTO.getTotal_fee());
        notifyData.put("out_trade_no", notifyDTO.getOut_trade_no());
        notifyData.put("payjs_order_id", notifyDTO.getPayjs_order_id());
        notifyData.put("transaction_id", notifyDTO.getTransaction_id());
        notifyData.put("time_end", notifyDTO.getTime_end());
        notifyData.put("openid", notifyDTO.getOpenid());
        notifyData.put("mchid", notifyDTO.getMchid());

        // options
        if (notifyDTO.getAttach() != null) {
            notifyData.put("attach", notifyDTO.getAttach());
        }
        if (notifyDTO.getType() != null) {
            notifyData.put("type", notifyDTO.getType());
        }
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", notifyDTO.getOut_trade_no()));
        String key = null;
        if (notifyDTO.getType() != null) { // ?????????
            Pays aliPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "payjs_alipay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(aliPays.getDriver())) {
                return "?????????????????????";
            }

            Map wxMap = JSON.parseObject(aliPays.getConfig());
            key = wxMap.get("key").toString();
        } else { // ??????
            Pays wxPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "payjs_wxpay"));

            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(wxPays.getDriver())) {
                return "?????????????????????";
            }

            Map wxMap = JSON.parseObject(wxPays.getConfig());
            key = wxMap.get("key").toString();
        }

        String sign = SignUtil.sign(notifyData, key);
        if (sign.equals(notifyDTO.getSign())) {
            AtomicReference<String> notifyText = new AtomicReference<>();
            synchronizedByKeyService.exec(notifyDTO.getOut_trade_no(), () -> {
                String returnBig = returnBig(notifyDTO.getTotal_fee(), notifyDTO.getTotal_fee(), notifyDTO.getOut_trade_no(), notifyDTO.getTransaction_id(), notifyDTO.getAttach(), "success", "failure");
                notifyText.set(returnBig);
            });
            return notifyText.get();
        } else {
            return "failure";
        }
    }

    /**
     * ????????????????????????
     *
     * @param request
     * @param response
     * @return
     */
    @RequestMapping("/wxpay/notify")
    @ResponseBody
    public String wxPayNotify(HttpServletRequest request, HttpServletResponse response) {
        String resXml = "";
        InputStream inStream;
        try {
            inStream = request.getInputStream();
            ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = inStream.read(buffer)) != -1) {
                outSteam.write(buffer, 0, len);
            }
            System.out.println("wxnotify:????????????----start----");
            // ????????????????????????notify_url???????????????
            String result = new String(outSteam.toByteArray(), "utf-8");
            System.out.println("wxnotify:????????????----result----=" + result);

            // ?????????
            outSteam.close();
            inStream.close();

            // xml?????????map
            Map<String, String> resultMap = WXPayUtil.xmlToMap(result);
            boolean isSuccess = false;
            String result_code = resultMap.get("result_code");
            String out_trade_no = resultMap.get("out_trade_no");// ???????????????????????????
            if ("SUCCESS".equals(result_code)) {
                Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "wxpay"));

                /**
                 * ??????????????????
                 */
                Orders orders1 = ordersService.getOne(new QueryWrapper<Orders>().eq("member", out_trade_no));
                if (!orders1.getPayType().equals(pays.getDriver())) {
                    return "?????????????????????";
                }

                Map mapTypes = JSON.parseObject(pays.getConfig());
                String key = mapTypes.get("key").toString(); // ??????

                /**
                 * ????????????
                 */
                if (WXPayUtil.isSignatureValid(resultMap, key)) {
                    String total_fee = resultMap.get("total_fee");// ??????????????????????????????
                    String transaction_id = resultMap.get("transaction_id");// ?????????????????????
                    String attach = resultMap.get("attach");// ??????????????????????????????
                    String appid = resultMap.get("appid");// ????????????????????????ID

                    BigDecimal bigDecimal = new BigDecimal(total_fee);
                    BigDecimal multiply = bigDecimal.divide(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_DOWN);
                    String money = new DecimalFormat("0.##").format(multiply);
                    Orders member = ordersService.getOne(new QueryWrapper<Orders>().eq("member", out_trade_no));
                    if (member.getPayType().equals("wxpay")) {
                        AtomicReference<String> notifyText = new AtomicReference<>();
                        synchronizedByKeyService.exec(out_trade_no, () -> {
                            String returnBig = returnBig(money, money, out_trade_no, transaction_id, attach, WxpayresXml, resFailXml);
                            notifyText.set(returnBig);
                        });
                        resXml = notifyText.get();
                    } else {
                        AtomicReference<String> notifyText = new AtomicReference<>();
                        synchronizedByKeyService.exec(out_trade_no, () -> {
                            String returnBig = returnBig(money, money, out_trade_no, transaction_id, attach, WxpayH5resXml, resFailXml);
                            notifyText.set(returnBig);
                        });
                        resXml = notifyText.get();
                    }
                } else {
                    System.out.println("????????????????????????");
                }
            }
        } catch (Exception e) {
            System.out.println("wxnotify:???????????????????????????" + e);
        } finally {
            try {
                // ??????????????????
                BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream());
                out.write(resXml.getBytes());
                out.flush();
                out.close();
            } catch (IOException e) {
                System.out.println("wxnotify:????????????????????????:out???" + e);
            }
        }
        return resXml;
    }

    /**
     * ?????????????????? ????????????
     *
     * @param request ??????
     * @return ??????
     */
    @RequestMapping("/alipay/notify")
    @ResponseBody
    public String alipayNotifyUrl(HttpServletRequest request) {

        System.out.println("1111111111111");
        String success = "success";
        String failure = "failure";

        Map<String, String> params = new HashMap<>();
        Map requestParams = request.getParameterMap();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        /**
         * ????????????,??????????????????????????????sign ????????????
         * ????????????????????????,????????????????????????.?????????????????????????????????.
         */
        params.remove("sign_type");

        String out_trade_no = params.get("out_trade_no");// ???????????????
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", out_trade_no));

        String alipay_public_key = null;
        Integer IS_ALIPAY_TYPE = 1;
        if ("alipay".equals(orders.getPayType())) {
            Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "alipay"));
            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(pays.getDriver())) {
                return "?????????????????????";
            }
            Map mapTypes = JSON.parseObject(pays.getConfig());
            alipay_public_key = mapTypes.get("alipay_public_key").toString(); // ??????
            IS_ALIPAY_TYPE = 1;
        } else if ("alipay_pc".equals(orders.getPayType())) {
            Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "alipay_pc"));
            /**
             * ??????????????????
             */
            if (!orders.getPayType().equals(pays.getDriver())) {
                return "?????????????????????";
            }
            Map mapTypes = JSON.parseObject(pays.getConfig());
            alipay_public_key = mapTypes.get("alipay_public_key").toString(); // ??????
            IS_ALIPAY_TYPE = 2;
        }

        try {
            boolean alipayRSAChecked = false;
            if (IS_ALIPAY_TYPE == 1) {
                alipayRSAChecked = AlipaySignature.rsaCheckV2(params, alipay_public_key, "utf-8", "RSA2");
            } else if (IS_ALIPAY_TYPE == 2) {
                alipayRSAChecked = AlipaySignature.rsaCheckV1(params, alipay_public_key, "utf-8", "RSA2");
            }

            if (alipayRSAChecked) {
                String total_amount = params.get("total_amount");// ????????????
                String trade_no = params.get("trade_no");// ??????
                String receipt_amount = params.get("receipt_amount");// ??????????????????
                String body = params.get("body");// ??????
                AtomicReference<String> notifyText = new AtomicReference<>();
                synchronizedByKeyService.exec(out_trade_no, () -> {
                    String returnBig1 = returnBig(receipt_amount, total_amount, out_trade_no, trade_no, body, success, failure);
                    notifyText.set(returnBig1);
                });
                return notifyText.get();
            } else {
                System.out.println("??????????????????");
                return failure;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return failure;
        }
    }

    /**
     * ?????????PC??????????????????
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping("/alipay/return_url")
    public void alipayReturnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException, AlipayApiException {

        // ????????????
        String sign_type = "RSA2";

        // ??????????????????
        String charset = "utf-8";

        /**
         *???????????? ?????????????????????
         */;
        // ???????????????GET??????????????????
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = iter.next();
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        Pays aliPays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "alipay_pc"));
        Map aliMap = JSON.parseObject(aliPays.getConfig());
        String alipay_public_key = aliMap.get("alipay_public_key").toString();

        // ??????SDK????????????
        boolean signVerified = AlipaySignature.rsaCheckV1(params,
                alipay_public_key,
                charset,
                sign_type); //??????SDK????????????
        // ????????????
        if (signVerified) {
            String pay_no = params.get("trade_no"); // ?????????
            String member = params.get("out_trade_no");// ???????????????
            if (pay_no != null || pay_no != "") {
                String url = "/search/order/" + member;
                response.sendRedirect(url);
            }
        } else {
            System.out.println("??????, ????????????...");
        }

    }

    /**
     * ????????????
     *
     * @return
     */
    @GetMapping("/paypal/cancel")
    @ResponseBody
    public String cancelPay() {
        return "cancel";
    }

    /**
     * ????????????
     *
     * @param paymentId
     * @param payerId
     * @param response
     * @return
     */
    @GetMapping("/paypal/success")
    @ResponseBody
    public String successPay(@RequestParam("paymentId") String paymentId, @RequestParam("PayerID") String payerId, HttpServletResponse response) {
        try {
            Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "paypal"));
            Map mapTypes = JSON.parseObject(pays.getConfig());
            String clientId = mapTypes.get("clientId").toString();
            String clientSecret = mapTypes.get("clientSecret").toString();
            Payment payment = PaypalSend.executePayment(clientId, clientSecret, paymentId, payerId);
            if (payment.getState().equals("approved")) {
                String member = null; // ?????????
                String total = null;  // ??????
                String pay_no = payment.getId();
                List<Transaction> transactions = payment.getTransactions();
                for (Transaction transaction : transactions) {
                    member = transaction.getDescription();
                    total = transaction.getAmount().getTotal(); // ??????????????????
                }
                Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", member));
                /**
                 * ??????????????????
                 */
                if (!orders.getPayType().equals(pays.getDriver())) {
                    return "?????????????????????";
                }
                String returnBig = returnBig(total, orders.getPrice().toString(), member, pay_no, orders.getProductId().toString(), "success", "failure");
                if (returnBig.equals("success")) {
                    response.sendRedirect("/search/order/" + member);
                } else {
                    response.sendRedirect("/search/order/" + member);
                }
            }
        } catch (PayPalRESTException | IOException e) {
            e.printStackTrace();
        }
        return "redirect:/";
    }

    /**
     * ????????????
     *
     * @param money   ???????????????
     * @param price   ????????????
     * @param payId   ?????????
     * @param pay_no  ?????????
     * @param param   ???????????????
     * @param success ????????????
     * @param fiald   ????????????
     * @return this
     */
    private String returnBig(String money, String price, String payId, String pay_no, String param, String success, String fiald) {

        System.out.println("payId = " + payId);

        /**
         * ?????????????????????
         */
        Orders member = ordersService.getOne(new QueryWrapper<Orders>().eq("member", payId));
        if (member == null) {
            return "????????????????????????"; // ????????????????????????
        }

        if (member.getStatus() > 0) {
            return success;
        }

        boolean empty = StringUtils.isEmpty(member.getCardsInfo());
        if (!empty) {
            return success;
        }

        Products products = productsService.getById(param);
        if (products == null) {
            return "??????????????????"; // ????????????
        }

        Website website = websiteService.getById(1);
        ShopSettings shopSettings = shopSettingsService.getById(1);

        Orders orders = new Orders();
        orders.setId(member.getId());
        orders.setPayTime(new Date());
        orders.setPayNo(pay_no);
        orders.setPrice(new BigDecimal(price));
        orders.setMoney(new BigDecimal(money));

        if (products.getShipType() == 0) { // ?????????????????????

            /**
             * ??????????????????
             * ????????????????????????????????????????????????????????????
             */
            if (products.getSellType() == 0) { // ?????????????????????

                List<Cards> cardsList = cardsService.getBaseMapper().selectList(new QueryWrapper<Cards>()
                        .eq("status", 0)
                        .eq("product_id", products.getId())
                        .eq("sell_type", 0)
                        .orderBy(true, false, "rand()")
                        .last("LIMIT " + member.getNumber() + ""));

                if (cardsList == null) return fiald; // ????????????????????????????????????

                StringBuilder orderInfo = new StringBuilder(); // ???????????????????????????
                List<Cards> updateCardsList = new ArrayList<>();
                for (Cards cards : cardsList) {
                    orderInfo.append(cards.getCardInfo()).append("\n"); // ??????StringBuilder ?????????????????????

                    /**
                     * ?????????????????????????????????????????????
                     */
                    Cards cards1 = new Cards();
                    cards1.setId(cards.getId());
                    cards1.setStatus(1);
                    cards1.setNumber(0);
                    cards1.setSellNumber(1);
                    cards1.setUpdatedAt(new Date());

                    updateCardsList.add(cards1);
                }

                // ???????????????????????????
                String result = orderInfo.deleteCharAt(orderInfo.length() - 1).toString();

                orders.setStatus(1); // ???????????????
                orders.setCardsInfo(result);

                // ?????????????????????
                if (ordersService.updateById(orders)) {
                    // ?????????????????????
                    cardsService.updateBatchById(updateCardsList);
                } else {
                    return fiald;
                }
            } else if (products.getSellType() == 1) { // ?????????????????????
                StringBuilder orderInfo = new StringBuilder(); // ???????????????????????????

                Cards cards = cardsService.getOne(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("status", 0).eq("sell_type", 1));
                if (cards == null) {
                    return fiald; // ????????????????????????????????????
                }

                /**
                 * ?????????????????????????????????????????????
                 */
                Cards cards1 = new Cards();
                cards1.setId(cards.getId());
                cards1.setUpdatedAt(new Date());
                if (cards.getNumber() == 1) { // ?????????????????????
                    cards1.setSellNumber(cards.getSellNumber() + member.getNumber());
                    cards1.setNumber(cards.getNumber() - member.getNumber()); // ??????????????????0
                    cards1.setStatus(1); // ??????????????????????????????
                } else {
                    cards1.setSellNumber(cards.getSellNumber() + member.getNumber());
                    cards1.setNumber(cards.getNumber() - member.getNumber());
                }

                /**
                 * ?????????????????????????????????
                 * ?????????????????????????????????1?????????
                 * ????????????????????????????????????????????????
                 */
                for (int i = 0; i < member.getNumber(); i++) {
                    orderInfo.append(cards.getCardInfo()).append("\n");
                }

                // ???????????????????????????
                String result = orderInfo.deleteCharAt(orderInfo.length() - 1).toString();
                orders.setStatus(1); // ???????????????
                orders.setCardsInfo(result);

                // ?????????????????????
                if (ordersService.updateById(orders)) {
                    cardsService.updateById(cards1);
                } else {
                    return fiald;
                }
            }

            /**
             * ????????? wxpush ??????
             * ????????????????????????
             * ??????????????????????????????????????????
             * wxpush ???????????????????????????????????????????????????
             */
            if (shopSettings.getIsWxpusher() == 1) {
                Message message = new Message();
                message.setContent(website.getWebsiteName() + "???????????????<br>????????????<span style='color:red;'>" + member.getMember() + "</span><br>???????????????<span>" + products.getName() + "</span><br>???????????????<span>" + member.getNumber() + "</span><br>???????????????<span>" + member.getMoney() + "</span><br>???????????????<span style='color:green;'>??????</span><br>");
                message.setContentType(Message.CONTENT_TYPE_HTML);
                message.setUid(shopSettings.getWxpushUid());
                message.setAppToken(shopSettings.getAppToken());
                WxPusher.send(message);
            }

            /**
             * ????????????
             * ???????????????????????????
             * ???????????????????????????????????????????????????
             */
            if (shopSettings.getIsEmail() == 1) {
                if (!StringUtils.isEmpty(member.getEmail())) {
                    if (FormCheckUtil.isEmail(member.getEmail())) {
                        Map<String, Object> map = new HashMap<>();  // ?????????????????????
                        map.put("title", website.getWebsiteName());
                        map.put("member", member.getMember());
                        map.put("date", DateUtil.getDate());
                        map.put("password", member.getPassword());
                        map.put("url", website.getWebsiteUrl() + "/search/order/" + member.getMember());
                        try {
                            emailService.sendHtmlEmail(website.getWebsiteName() + "????????????", "email/sendShip.html", map, new String[]{member.getEmail()});
                            // emailService.sendTextEmail("??????????????????", "?????????????????????" + member.getMember() + "  ???????????????" + cards.getCardInfo(), new String[]{member.getEmail()});
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else { // ??????????????????
            Products products1 = new Products();
            products1.setId(products.getId());
            products1.setInventory(products.getInventory() - member.getNumber());
            products1.setSales(products.getSales() + member.getNumber());

            orders.setStatus(2); // ?????????????????? ????????????
            if (ordersService.updateById(orders)) {
                // ????????????
                productsService.updateById(products1);
            } else {
                return fiald;
            }

            /**
             * ????????? wxpush ??????
             * ????????????????????????
             * ??????????????????????????????????????????
             * wxpush ???????????????????????????????????????????????????
             */
            if (shopSettings.getIsWxpusher() == 1) {
                Message message = new Message();
                message.setContent(website.getWebsiteName() + "???????????????<br>????????????<span style='color:red;'>" + member.getMember() + "</span><br>???????????????<span>" + products.getName() + "</span><br>???????????????<span>" + member.getNumber() + "</span><br>???????????????<span>" + member.getMoney() + "</span><br>???????????????<span style='color:green;'>??????</span><br>");
                message.setContentType(Message.CONTENT_TYPE_HTML);
                message.setUid(shopSettings.getWxpushUid());
                message.setAppToken(shopSettings.getAppToken());
                WxPusher.send(message);
            }

            /**
             * ????????????
             * ???????????????????????????
             * ???????????????????????????????????????????????????
             */
            if (shopSettings.getIsEmail() == 1) {
                if (FormCheckUtil.isEmail(member.getEmail())) {
                    try {
                        emailService.sendTextEmail(website.getWebsiteName() + " ????????????", "?????????????????????" + member.getMember() + "  ?????????????????????????????????????????????", new String[]{member.getEmail()});
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return success;
    }

    @GetMapping("/order/state/{orderid}")
    @ResponseBody
    public JsonResult state(@PathVariable("orderid") String orderid) {
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("id", orderid));
        if (!StringUtils.isEmpty(orders.getPayNo())) {
            return JsonResult.ok().setCode(200).setData(1);
        } else {
            return JsonResult.ok().setData(0);
        }
    }


    public static String packageSign(Map<String, String> params, boolean urlEncoder) {
        // ?????????????????????????????????????????????????????????
        TreeMap<String, String> sortedParams = new TreeMap<String, String>(params);
        // ?????????????????????????????????????????????"key=value"?????????????????????
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> param : sortedParams.entrySet()) {
            String value = param.getValue();
            if (org.apache.commons.lang3.StringUtils.isBlank(value)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append("&");
            }
            sb.append(param.getKey()).append("=");
            if (urlEncoder) {
                try {
                    value = urlEncode(value);
                } catch (UnsupportedEncodingException e) {
                }
            }
            sb.append(value);
        }
        return sb.toString();
    }

    public static String urlEncode(String src) throws UnsupportedEncodingException {
        return URLEncoder.encode(src, Charsets.UTF_8.name()).replace("+", "%20");
    }

    public static String createSign(Map<String, String> params, String partnerKey) throws NoSuchAlgorithmException {
        // ????????????????????????sign
        params.remove("sign");
        String stringA = packageSign(params, false);
        String stringSignTemp = stringA + "&key=" + partnerKey;

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update((stringSignTemp).getBytes());
        String mySign = new BigInteger(1, md.digest()).toString(16).toUpperCase();
        if (mySign.length() != 32) {
            mySign = "0" + mySign;
        }
        return mySign;
    }

}
