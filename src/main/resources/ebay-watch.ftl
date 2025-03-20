<head>
<link href="${cssHostingBaseUrl}/newitem.css" rel="stylesheet" type="text/css">
</head>
<body marginwidth="0" marginheight="0">
    <table align="center" style="border-spacing: 0px; width: 100%;">
        <tbody>
            <tr>
                <td>
                    <div id="ds_div">
                        <table align="center" style="border-spacing: 0px; width: 100%;">
                            <tbody>
                                <tr>
                                    <td>
                                        <div id="ds_div">
                                            <div class="ieAlign">
                                                <div class="Bot">
                                                    <div class="Outer">
                                                        <div id="OuterContainer">
                                                            <div class="userNote">
								                                <!-- Put a link to a image for announcements." -->
                                                            </div>
                                                            <div id="HeaderContainer">
                                                                <div>
                                                                    <div class="main-head">
                                                                        <div class="shoplogo">
                                                                            <div class="logo">
										                                          <img alt="logo Watch Dealers" src="${cssHostingBaseUrl}/images/logo.png" width="300">
                                                                            </div>
                                                                            <div class="tagline">344 North Beverly Drive, Beverly Hills, CA 90210 </div>
 									                                    </div>
                                                                        <div id="topNav"></div>
                                                                        <div class="eclear"></div>
                                                                    </div>
                                                                    <div class="eclear"></div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <!--/Header-->
                                                        <div id="ContentsContainer">
                                                            <div class="indent">
                                                                <div id="CenterContainer">
                                                                    <div id="outerBoxitem1">
                                                                        <div class="vTitle" id="editable1">
                                                                            <p>${webDescriptionShort}</p>
                                                                        </div>
                                                                        <div class="slot">
                                                                            <div id="prdDesc">
                                                                                <div class="totalImgContainer">
                                                                                    <div class="col-right">
                                                                                          <h3 class="subTitile" style="padding:0 10px 0; color:#666; border-bottom:1px solid #eee; font-size:18px; text-align:center; ">Item description and Images</h3>
                                                                                          <#if webImagePath1??>
                                                                                            <img id="currentImage" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-1.jpg" height="283">
                                                                                          </#if> 
                                                                                              <ul>
                                                                                                <#if webDesigner??>
                                                                                                    <li><b>Brand</b>: ${webDesigner}</li>
                                                                                                </#if>
                                                                                                <#if webWatchModel??>
                                                                                                    <li><b>Model</b>: ${webWatchModel}</li>
                                                                                                </#if>
                                                                                                <#if webWatchManufacturerReferenceNumber??>
                                                                                                    <li><b>Reference</b>: ${webWatchManufacturerReferenceNumber}</li>
                                                                                                </#if>
                                                                                                <#if webStyle??>
                                                                                                    <li><b>Gender</b>: ${webStyle}</li>
                                                                                                </#if>
                                                                                                <#if webWatchYear??>
                                                                                                    <li><b>Year</b>: ${webWatchYear}</li>
                                                                                                </#if>                                                                                                
                                                                                                <#if webMetalType??>
                                                                                                    <li><b>Material</b>: ${webMetalType}</li>
                                                                                                </#if>
                                                                                                <#if webWatchDial??>
                                                                                                    <li><b>Dial Color</b>: ${webWatchDial}</li>
                                                                                                </#if>
                                                                                                <#if webWatchDiameter??>
                                                                                                    <li><b>Dimensions</b>: ${webWatchDiameter}</li>
                                                                                                </#if>
                                                                                                <#if movementCode??>
                                                                                                    <li><b>Watch Movement</b>: ${movementCode}</li>
                                                                                                </#if>
                                                                                                <#if webWatchStrap??>
                                                                                                    <li><b>Bracelet/Strap</b>: ${webWatchStrap}</li>
                                                                                                </#if>
                                                                                                <#if webWatchBoxPapers??>
                                                                                                    <li><b>Box/Paper</b>: ${webWatchBoxPapers}</li>
                                                                                                </#if>
                                                                                                <#if webWatchCondition??>
                                                                                                    <li><b>Condition</b>: ${webWatchCondition}</li>
                                                                                                </#if>
                                                                                                <li><b>SKU</b>: ${webTagNumber}</li>
                                                                                              </ul>
                                                                                    </div>
                                                                                
                                                                                <div class="" id="">
                                                                                    <div class="prev_thumb">
                                                                                        <#if webImagePath2??>
                                                                                            <img id="item_image1" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-1.jpg" height="283" >
                                                                                        </#if> 
                                                                                        <#if webImagePath2??>
                                                                                            <img id="item_image2" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-2.jpg" height="283" >
                                                                                        </#if> 
                                                                                        <#if webImagePath3??>
                                                                                            <img id="item_image3" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-3.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath4??>
                                                                                            <img id="item_image4" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-4.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath5??>
                                                                                            <img id="item_image5" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-5.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath6??>
                                                                                            <img id="item_image6" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-6.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath7??>
                                                                                            <img id="item_image7" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-7.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath8??>
                                                                                            <img id="item_image8" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-8.jpg" height="283" >
                                                                                        </#if>
                                                                                        <#if webImagePath9??>
                                                                                            <img id="item_image9" src="${cssHostingBaseUrl}/images/watches/${webTagNumber}-9.jpg" height="283" >
                                                                                        </#if>
                                                                                    </div>
                                                                                    <div class="eclear"><img src="${cssHostingBaseUrl}/images/spacer.gif">
                                                                                    </div>
                                                                                </div>
                                                                                <div class="eclear"><img src="${cssHostingBaseUrl}/images/spacer.gif">
                                                                                </div>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                    <!--/OuterBox Item Description-->
                                                                        <div class="footer">
                                                                            <ul>
                                                                              <li>
                                                                                <h3>Experience Matters</h3>
                                                                                <p>With over 40 years of experience, GRUENBERG WATCHES has established itself as a leading shop for high-end wristwatches with a particular emphasis on rare and hard-to-find pieces.</p>
                                                                                <p>Founder, Donald Gruenberg along with several others, travel the world to source their watches where they meet with private collectors and international dealers.</p>
                                                                                <p>In the 1980's, Donald Gruenberg was one of the most prominent dealers of collectible watches and has continued to be an important dealer for some of the worlds rarest pieces. </p>
                                                                                <p>The Gruenberg brick and mortar shop is a staple in Beverly Hills where it's stood for over 40 years.
                                                                              </li>
                                                                              <li>
                                                                                <h3>Attention to Details</h3>
                                                                                <p>Each piece at GRUENBERG WATCHES is handled with the utmost care. Attention to detail and complete transparency is everything to us. Each watch is examined prior to purchase and goes through extensive tests for accurate timekeeping and authenticity.</p>
                                                                                <p>Our in-house watchmaker certifies all watches with a one-year service warranty just in case a mechanical problem occurs. And of course we are available to help with your question or concern so feel free to call us, Monday-Friday, 10am-5pm PST at +1 (310) 273-4577.</p>
                                                                                </li>
                                                                                <li>
                                                                                  <h3>Thanks and Happy Watch-Hunting! </h3>
                                                                                </li>
                                                                            </ul>
                                                                            <div class="btmImg">
                                                                              <img src="${cssHostingBaseUrl}/images/FullSizeRender.jpg">
                                                                            </div>
                                                                            <div class="eclear"><img src="${cssHostingBaseUrl}/images/spacer.gif"></div>
                                                                        </div>
                                                                    </div>
                                                                    <div class="eclear">
                                                                      <img src="${cssHostingBaseUrl}/images/spacer.gif">
                                                                    </div>
                                                                </div>
                                                                <!--/CenterContainer-->
                                                                <div class="eclear"><img src="${cssHostingBaseUrl}/images/spacer.gif">
                                                                </div>
                                                            </div>
                                                            <div class="eclear"><img src="${cssHostingBaseUrl}/images/spacer.gif">
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <!--Item ends-->
                                        <!--esm-->
                                        
                                        <div id="esmTemplate" style="padding: 10px; text-align: center; clear: both;"></div>
                                    </td>
                                </tr>
                            </tbody>
                        </table><span id="closeHtml"></span>
                    </div>
                </td>
            </tr>
        </tbody>
    </table><span id="closeHtml"></span>
</body>
