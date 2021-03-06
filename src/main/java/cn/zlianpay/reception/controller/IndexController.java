package cn.zlianpay.reception.controller;

import cn.zlianpay.carmi.entity.Cards;
import cn.zlianpay.carmi.service.CardsService;
import cn.zlianpay.common.core.enmu.Alipay;
import cn.zlianpay.common.core.enmu.Paypal;
import cn.zlianpay.common.core.enmu.QQPay;
import cn.zlianpay.common.core.enmu.Wxpay;
import cn.zlianpay.common.core.utils.DateUtil;
import cn.zlianpay.common.core.web.JsonResult;
import cn.zlianpay.common.core.web.PageParam;
import cn.zlianpay.common.core.web.PageResult;
import cn.zlianpay.common.system.entity.User;
import cn.zlianpay.common.system.service.UserService;
import cn.zlianpay.content.entity.Article;
import cn.zlianpay.content.entity.Carousel;
import cn.zlianpay.content.service.ArticleService;
import cn.zlianpay.content.service.CarouselService;
import cn.zlianpay.reception.dto.HotProductDTO;
import cn.zlianpay.reception.dto.ProductDTO;
import cn.zlianpay.reception.dto.SearchDTO;
import cn.zlianpay.reception.util.ProductUtil;
import cn.zlianpay.reception.vo.ArticleVo;
import cn.zlianpay.settings.entity.Coupon;
import cn.zlianpay.settings.entity.ShopSettings;
import cn.zlianpay.settings.service.CouponService;
import cn.zlianpay.settings.service.ShopSettingsService;
import cn.zlianpay.settings.vo.PaysVo;
import cn.zlianpay.theme.entity.Theme;
import cn.zlianpay.theme.service.ThemeService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.zlianpay.orders.entity.Orders;
import cn.zlianpay.orders.service.OrdersService;
import cn.zlianpay.orders.vo.OrdersVo;
import cn.zlianpay.products.entity.Classifys;
import cn.zlianpay.products.entity.Products;
import cn.zlianpay.products.service.ClassifysService;
import cn.zlianpay.products.service.ProductsService;
import cn.zlianpay.products.vo.ClassifysVo;
import cn.zlianpay.products.vo.ProductsVos;
import cn.zlianpay.settings.entity.Pays;
import cn.zlianpay.settings.service.PaysService;
import cn.zlianpay.website.entity.Website;
import cn.zlianpay.website.service.WebsiteService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mobile.device.Device;
import org.springframework.mobile.device.DeviceUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Controller
public class IndexController {

    @Autowired
    private ProductsService productsService;

    @Autowired
    private ClassifysService classifysService;

    @Autowired
    private CardsService cardsService;

    @Autowired
    private PaysService paysService;

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private WebsiteService websiteService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private ThemeService themeService;

    @Autowired
    private ShopSettingsService shopSettingsService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private UserService userService;

    @Autowired
    private CarouselService carouselService;

    /**
     * ??????
     * @param model
     * @return
     */
    @RequestMapping({"/", "/index"})
    public String IndexView(Model model) {
        /**
         * ????????????
         * ??????????????????????????????
         */
        List<Classifys> classifysList = classifysService.list(Wrappers.<Classifys> lambdaQuery().eq(Classifys::getStatus, 1).orderByAsc(Classifys::getSort));
        AtomicInteger index = new AtomicInteger(0); // ??????
        List<ClassifysVo> classifysVoList = classifysList.stream().map((classifys) -> {
            ClassifysVo classifysVo = new ClassifysVo();
            BeanUtils.copyProperties(classifys, classifysVo);
            int count = productsService.count(Wrappers.<Products> lambdaQuery().eq(Products::getClassifyId, classifys.getId()).eq(Products::getStatus, 1));
            classifysVo.setProductsMember(count);
            int andIncrement = index.getAndIncrement();
            classifysVo.setAndIncrement(andIncrement); // ??????
            return classifysVo;
        }).collect(Collectors.toList());

        model.addAttribute("classifysListJson", JSON.toJSONString(classifysVoList));

        /**
         * ?????????
         */
        List<Carousel> carouselList = carouselService.list(Wrappers.<Carousel> lambdaQuery().eq(Carousel::getEnabled, 1));
        model.addAttribute("carouselList", carouselList);

        /**
         * ????????????????????????
         */
        List<Products> randomProductList = productsService.getRandomProductList(4);
        List<ProductDTO> productDTOList = getProductDTOList(randomProductList);
        List<HotProductDTO> hotProductList = ProductUtil.getHotProductList(productDTOList);
        model.addAttribute("hotProductList", hotProductList);

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());

        model.addAttribute("shopSettings", JSON.toJSONString(shopSettings));
        model.addAttribute("shop", shopSettings);

        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));
        return "theme/" + theme.getDriver() + "/index.html";
    }

    @RequestMapping("/article")
    public String articleView(Model model) {
        /**
         * ????????????????????????
         */
        List<Products> randomProductList = productsService.getRandomProductList(4);
        List<ProductDTO> productDTOList = getProductDTOList(randomProductList);
        List<HotProductDTO> hotProductList = ProductUtil.getHotProductList(productDTOList);
        model.addAttribute("hotProductList", hotProductList);

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));
        return "theme/" + theme.getDriver() + "/article.html";
    }

    /**
     * ????????????
     * @param model
     * @return
     */
    @RequestMapping("/article/{id}")
    public String articleContentView(Model model, @PathVariable("id") Integer id) {
        Article article = articleService.getById(id);
        ArticleVo articleVo = new ArticleVo();
        BeanUtils.copyProperties(article, articleVo);
        articleVo.setCreateTime(DateUtil.getSubDate(article.getCreateTime()));
        articleVo.setUpdateTime(DateUtil.getSubDate(article.getUpdateTime()));
        model.addAttribute("article", articleVo);

        /**
         * ??????????????????????????????
         */
        Article article1 = new Article();
        article1.setId(article.getId());
        article1.setSeeNumber(article.getSeeNumber() + 1);

        articleService.updateById(article1);

        /**
         * ????????????????????????
         */
        List<Products> randomProductList = productsService.getRandomProductList(4);
        List<ProductDTO> productDTOList = getProductDTOList(randomProductList);
        List<HotProductDTO> hotProductList = ProductUtil.getHotProductList(productDTOList);
        model.addAttribute("hotProductList", hotProductList);

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));
        return "theme/" + theme.getDriver() + "/article-content.html";
    }

    /**
     * ??????????????????
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping("/getArticleList")
    public PageResult<ArticleVo> getArticleList(HttpServletRequest request) {
        PageParam<Article> pageParam = new PageParam<>(request);
        pageParam.put("enabled", 1);
        List<Article> articleList = articleService.page(pageParam, pageParam.getWrapper()).getRecords();
        List<ArticleVo> articleVoList = articleList.stream().map((article) -> {
            ArticleVo articleVo = new ArticleVo();
            BeanUtils.copyProperties(article, articleVo);
            User user = userService.getOne(new QueryWrapper<User>().eq("user_id", article.getUserId()));
            articleVo.setUserName(user.getNickName());
            articleVo.setUserHead(user.getAvatar());
            articleVo.setCreateTime(DateUtil.getSubDate(article.getCreateTime()));
            articleVo.setUpdateTime(DateUtil.getSubDate(article.getUpdateTime()));
            return articleVo;
        }).collect(Collectors.toList());
        return new PageResult<>(articleVoList, pageParam.getTotal());
    }

    @ResponseBody
    @RequestMapping("/getProductList")
    public PageResult<ProductDTO> getProductList(Integer page, Integer limit, Integer classifyId, String name) {
        if (ObjectUtils.isEmpty(classifyId)) return new PageResult<>(null, 0).setCode(1001).setMsg("??????????????????");
        IPage<Products> productIPage = new Page<>(page, limit);
        IPage<Products> productIPageList = productsService.page(productIPage, Wrappers.<Products> lambdaQuery()
                .eq(Products::getStatus, 1)
                .eq(Products::getClassifyId, classifyId)
                .like(!StringUtils.isEmpty(name), Products::getName, name)
                .orderByAsc(Products::getSort));
        List<ProductDTO> productDTOList = getProductDTOList(productIPageList.getRecords());
        if (ObjectUtils.isEmpty(productDTOList) && productIPage.getTotal() == 0) return new PageResult<>(productDTOList, productIPageList.getTotal()).setCode(1000);
        return new PageResult<>(productDTOList, productIPageList.getTotal());
    }

    /**
     * ??????????????????
     * @param model
     * @param link
     * @return
     */
    @RequestMapping("/product/{link}")
    public String product(Model model, @PathVariable("link") String link, HttpServletRequest request) {
        Website website = websiteService.getById(1);
        model.addAttribute("website", website);
        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));

        // ????????????
        Products products = productsService.getOne(new QueryWrapper<Products>().eq("link", link));

        /**
         * ????????????
         * ?????????????????????????????????
         */
        if (ObjectUtils.isEmpty(products)) {
            return "theme/" + theme.getDriver() + "/product404.html";
        }

        // ????????????
        Classifys classifys = classifysService.getById(products.getClassifyId());

        Device currentDevice = DeviceUtils.getCurrentDevice(request);
        AtomicInteger index = new AtomicInteger(0);
        if (currentDevice.isMobile()) {
            List<PaysVo> paysVoList = getPaysVoList(paysService.list(new QueryWrapper<Pays>().eq("is_mobile", 1)), index);
            model.addAttribute("paysList", paysVoList);
        } else {
            List<PaysVo> paysVoList = getPaysVoList(paysService.list(new QueryWrapper<Pays>().eq("is_pc", 1)), index);
            model.addAttribute("paysList", paysVoList);
        }

        model.addAttribute("products", products);
        model.addAttribute("productsJson", JSON.toJSONString(getProductById(products)));
        model.addAttribute("classifyName", classifys.getName());

        /**
         * ??????????????????
         */
        if (products.getIsWholesale() == 1) {
            String wholesale = products.getWholesale();
            String[] wholesales = wholesale.split("\\n");
            List<Map<String, String>> list = new ArrayList<>();
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (String s : wholesales) {
                String[] split = s.split("=");
                Map<String, String> map = new HashMap<>();
                Integer andIncrement = atomicInteger.getAndIncrement();
                map.put("id", andIncrement.toString());
                map.put("number", split[0]);
                map.put("money", split[1]);
                list.add(map);
            }
            model.addAttribute("wholesaleList", list);
        }

        /**
         * ???????????????????????????
         */
        int isCoupon = couponService.count(new QueryWrapper<Coupon>().eq("product_id", products.getId()));
        model.addAttribute("isCoupon", isCoupon);

        /**
         * ??????????????????????????????
         */
        Integer isCustomize = products.getIsCustomize();
        model.addAttribute("isCustomize", isCustomize);
        if (isCustomize == 1) {
            if (!StringUtils.isEmpty(products.getCustomizeInput())) {
                String customizeInput = products.getCustomizeInput();
                String[] customize = customizeInput.split("\\n");
                List<Map<String, String>> list = new ArrayList<>();
                for (String s : customize) {
                    String[] split = s.split("=");
                    Map<String, String> map = new HashMap<>();
                    map.put("field", split[0]);
                    map.put("name", split[1]);
                    map.put("switch", split[2]);
                    list.add(map);
                }
                model.addAttribute("customizeList", list);
            }
        }

        if (products.getShipType() == 0) { // ??????????????????
            Integer count = getCardListCount(cardsService, products); // ????????????????????????
            model.addAttribute("cardCount", count);
        } else { // ??????????????????
            model.addAttribute("cardCount", products.getInventory());
        }

        if (products.getSellType() == 1) {
            Cards cards = cardsService.getOne(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("status", 0).eq("sell_type", 1));
            if (!ObjectUtils.isEmpty(cards)) {
                model.addAttribute("cardCount", cards.getNumber());
            } else {
                model.addAttribute("cardCount", 0);
            }
        }

        return "theme/" + theme.getDriver() + "/product.html";
    }

    public List<PaysVo> getPaysVoList(List<Pays> paysList, AtomicInteger index) {
        List<PaysVo> paysVoList = paysList.stream().map((pays) -> {
            PaysVo paysVo = new PaysVo();
            BeanUtils.copyProperties(pays, paysVo);
            int andIncrement = index.getAndIncrement();
            paysVo.setAndIncrement(andIncrement); // ??????
            return paysVo;
        }).collect(Collectors.toList());
        return paysVoList;
    }

    public ProductsVos getProductById(Products products) {
        ProductsVos productsVos = new ProductsVos();
        BeanUtils.copyProperties(products, productsVos);
        productsVos.setId(products.getId());
        productsVos.setPrice(products.getPrice().toString());

        if (products.getShipType() == 0) { // ??????????????????
            Integer count = getCardListCount(cardsService, products); // ????????????????????????
            productsVos.setCardsCount(count.toString());
        } else { // ??????????????????
            productsVos.setCardsCount(products.getInventory().toString());
        }

        if (products.getSellType() == 1) {
            Cards cards = cardsService.getOne(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("status", 0).eq("sell_type", 1));
            if (!ObjectUtils.isEmpty(cards)) {
                productsVos.setCardsCount(cards.getNumber().toString());
            } else {
                productsVos.setCardsCount("0");
            }
        }
        return productsVos;
    }

    /**
     * ????????????????????????
     *
     * @param cardsService
     * @param products
     * @return
     */
    public static Integer getCardListCount(CardsService cardsService, Products products) {
        List<Cards> cardsList = cardsService.list(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("sell_type", 0));
        Integer count = 0;
        for (Cards cards : cardsList) {
            if (cards.getStatus() == 0) {
                count++;
            }
        }
        return count;
    }

    @RequestMapping("/search")
    public String search(Model model, @CookieValue(name = "BROWSER_ORDERS_CACHE", required = false) String orderCache) {
        if (!ObjectUtils.isEmpty(orderCache)) {
            String[] split = orderCache.split("=");
            List<SearchDTO> ordersList = new ArrayList<>();
            AtomicInteger index = new AtomicInteger(0);
            for (String s : split) {
                Orders member = ordersService.getOne(new QueryWrapper<Orders>().eq("member", s));
                if (ObjectUtils.isEmpty(member)) continue;
                SearchDTO searchDTO = new SearchDTO();
                searchDTO.setId(member.getId());
                Integer andIncrement = index.getAndIncrement();
                searchDTO.setAndIncrement(andIncrement);
                searchDTO.setMember(member.getMember());
                searchDTO.setCreateTime(DateUtil.getSubDateMiao(member.getCreateTime()));
                searchDTO.setMoney(member.getMoney().toString());
                if (Alipay.getByValue(member.getPayType())) {
                    searchDTO.setPayType("?????????");
                } else if (Wxpay.getByValue(member.getPayType())) {
                    searchDTO.setPayType("??????");
                } else if (Paypal.getByValue(member.getPayType())) {
                    searchDTO.setPayType("Paypal");
                } else if (QQPay.getByValue(member.getPayType())) {
                    searchDTO.setPayType("QQ??????");
                }
                switch (member.getStatus()) {
                    case 1:
                        searchDTO.setStatus("?????????");
                        break;
                    case 2:
                        searchDTO.setStatus("?????????");
                        break;
                    case 3:
                        searchDTO.setStatus("?????????");
                        break;
                    default:
                        searchDTO.setStatus("?????????");
                        break;
                }
                ordersList.add(searchDTO);
            }
            model.addAttribute("ordersList", JSON.toJSONString(ordersList));
        } else {
            List<SearchDTO> ordersList = new ArrayList<>();
            model.addAttribute("ordersList", JSON.toJSONString(ordersList));
        }

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);
        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));
        return "theme/" + theme.getDriver() + "/search.html";
    }

    @RequestMapping("/search/order/{order}")
    public String searchOrder(Model model, @PathVariable("order") String order) {
        Orders member = ordersService.getOne(Wrappers.<Orders> lambdaQuery().eq(Orders::getMember, order));
        Products products = productsService.getById(member.getProductId());

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);
        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        model.addAttribute("orderId", member.getId());
        model.addAttribute("member", member.getMember());
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));

        if (!StringUtils.isEmpty(products.getIsPassword())) {
            if (products.getIsPassword() == 1) {
                return "theme/" + theme.getDriver() + "/orderPass.html";
            }
        }
        Classifys classifys = classifysService.getById(products.getClassifyId());

        List<String> cardsList = new ArrayList<>();
        if (!StringUtils.isEmpty(member.getCardsInfo())) {
            String[] cardsInfo = member.getCardsInfo().split("\n");
            for (String cardInfo : cardsInfo) {
                StringBuilder cardInfoText = new StringBuilder();
                if (products.getShipType() == 0) {
                    cardInfoText.append(cardInfo).append("\n");
                    cardsList.add(cardInfoText.toString());
                } else {
                    cardInfoText.append(cardInfo);
                    cardsList.add(cardInfoText.toString());
                }
            }
        }

        OrdersVo ordersVo = new OrdersVo();
        BeanUtils.copyProperties(member, ordersVo);
        if (member.getPayTime() != null) {
            ordersVo.setPayTime(DateUtil.getSubDateMiao(member.getPayTime()));
        } else {
            ordersVo.setPayTime(null);
        }
        ordersVo.setMoney(member.getMoney().toString());
        /**
         * ????????????
         */
        ordersVo.setShipType(products.getShipType());
        model.addAttribute("cardsList", cardsList); // ??????
        model.addAttribute("orders", ordersVo); // ??????
        model.addAttribute("goods", products);  // ??????
        model.addAttribute("classify", classifys);  // ??????
        return "theme/" + theme.getDriver() + "/order.html";
    }

    /**
     * ????????????
     *
     * @param model
     * @return
     */
    @RequestMapping("/pay/state/{payId}")
    public String payState(Model model, @PathVariable("payId") String payId) {
        Orders orders = ordersService.getOne(new QueryWrapper<Orders>().eq("member", payId));
        model.addAttribute("orderId", orders.getId());
        model.addAttribute("ordersMember", orders.getMember());

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        ShopSettings shopSettings = shopSettingsService.getById(1);
        model.addAttribute("isBackground", shopSettings.getIsBackground());
        model.addAttribute("shop", shopSettings);
        Theme theme = themeService.getOne(Wrappers.<Theme> lambdaQuery().eq(Theme::getEnable, 1));
        return "theme/" + theme.getDriver() + "/payState.html";
    }

    @ResponseBody
    @GetMapping("/getProductSearchList")
    public JsonResult getProductSearchList(String content) {
        List<Products> productsList = productsService.list(Wrappers.<Products> lambdaQuery().eq(Products::getStatus, 1).like(Products::getName, content));
        List<ProductDTO> productDTOList = getProductDTOList(productsList);
        return JsonResult.ok("???????????????").setData(productDTOList);
    }

    /**
     * ??????????????????
     * @return
     */
    @ResponseBody
    @RequestMapping("/getShoppingNotes")
    public JsonResult getShoppingNotes() {
        ShopSettings shopSettings = shopSettingsService.getById(1);
        return JsonResult.ok().setData(shopSettings.getWindowText());
    }

    /* ???????????????
     * @author
     * @param
     * @return
     */
    @RequestMapping("/exportCards")
    public void exportCardsList(HttpServletResponse response, Integer orderId) {
        Orders orders = ordersService.getById(orderId);
        Products products = productsService.getById(orders.getProductId());
        StringBuffer text = new StringBuffer();
        if (!ObjectUtils.isEmpty(orders)) {
            String[] split = orders.getCardsInfo().split("\n");
            for (String s : split) {
                text.append(s).append("\n");
            }
        }
        exportTxt(response, products.getName() + "-" + orders.getMember(), text.toString());
    }

    /*
     * ??????txt??????
     * @author  Panyoujie
     * @param	response
     * @param	text ??????????????????
     * @return
     */
    public void exportTxt(HttpServletResponse response, String fileName, String text){
        response.setCharacterEncoding("utf-8");
        // ???????????????????????????
        response.setContentType("text/plain");
        // ??????????????????????????????
        response.addHeader("Content-Disposition","attachment;filename="
                + genAttachmentFileName(fileName, "JSON_FOR_UCC_") //?????????????????????????????????????????????????????????
                + ".txt");
        BufferedOutputStream buff = null;
        ServletOutputStream outStr = null;
        try {
            outStr = response.getOutputStream();
            buff = new BufferedOutputStream(outStr);
            buff.write(text.getBytes("UTF-8"));
            buff.flush();
            buff.close();
        } catch (Exception e) {
            //LOGGER.error("????????????????????????:{}",e);
        } finally {try {
            buff.close();
            outStr.close();
        } catch (Exception e) {
            //LOGGER.error("????????????????????? e:{}",e);
        }
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????
     * @param cnName
     * @param defaultName
     * @return
     */
    public  String genAttachmentFileName(String cnName, String defaultName) {
        try {
            cnName = new String(cnName.getBytes("gb2312"), "ISO8859-1");
        } catch (Exception e) {
            cnName = defaultName;
        }
        return cnName;
    }

    /**
     * ????????????????????????
     * ?????????????????????????????????
     * @param productsList
     * @return
     */
    public List<ProductDTO> getProductDTOList(List<Products> productsList) {
        List<ProductDTO> productDTOList = productsList.stream().map((products) -> {
            ProductDTO productDTO = new ProductDTO();
            BeanUtils.copyProperties(products, productDTO);
            int count = cardsService.count(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("status", 0).eq("sell_type", 0));
            productDTO.setCardMember(count);
            int count2 = cardsService.count(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("status", 1).eq("sell_type", 0));
            productDTO.setSellCardMember(count2);
            productDTO.setPrice(products.getPrice().toString());
            int count1 = couponService.count(new QueryWrapper<Coupon>().eq("product_id", products.getId()));
            productDTO.setIsCoupon(count1);
            if (products.getShipType() == 1) {
                productDTO.setCardMember(products.getInventory());
                productDTO.setSellCardMember(products.getSales());
            }
            if (products.getSellType() == 1) {
                Cards cards = cardsService.getOne(new QueryWrapper<Cards>().eq("product_id", products.getId()).eq("sell_type", 1));
                if (ObjectUtils.isEmpty(cards)) { // kon
                    productDTO.setCardMember(0);
                    productDTO.setSellCardMember(0);
                } else {
                    productDTO.setCardMember(cards.getNumber());
                    productDTO.setSellCardMember(cards.getSellNumber());
                }
            }
            return productDTO;
        }).collect(Collectors.toList());
        return productDTOList;
    }
}
