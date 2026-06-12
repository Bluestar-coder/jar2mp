package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePostProcessorTest {

    @Test
    void removesBroadObjectCasts() {
        String processed = new SourcePostProcessor().process(
                "redirectAttributes.addFlashAttribute(\"message\", (Object)\"ok\");\n"
                        + "repository.save((Object)owner);\n");

        assertFalse(processed.contains("(Object)"));
        assertTrue(processed.contains("repository.save(owner);"));
    }

    @Test
    void stripsDecompilerHeader() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR 0.152.\n"
                        + " * \n"
                        + " * Could not load the following classes:\n"
                        + " *  demo.Dependency\n"
                        + " */\n"
                        + "package demo;\n\npublic class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("Decompiled with"));
        assertTrue(processed.startsWith("package demo;"));
    }

    @Test
    void stripsOnlyDecompilerHeaderAndPreservesPackageImports() {
        String processed = new SourcePostProcessor().process(
                "/*\n"
                        + " * Decompiled with CFR.\n"
                        + " */\n"
                        + "package demo;\n\n"
                        + "import java.util.List;\n\n"
                        + "/*\n"
                        + " * This class specifies class file version 49.0.\n"
                        + " */\n"
                        + "public class Sample { List<String> values; }\n",
                "demo.Sample");

        assertTrue(processed.startsWith("package demo;"));
        assertTrue(processed.contains("import java.util.List;"));
        assertTrue(processed.contains("public class Sample"));
    }

    @Test
    void removesImplicitImports() {
        String processed = new SourcePostProcessor().process(
                "package demo;\n\n"
                        + "import demo.Helper;\n"
                        + "import java.lang.String;\n"
                        + "import demo.nested.Other;\n"
                        + "import java.util.List;\n\n"
                        + "public class Sample {}\n",
                "demo.Sample");

        assertFalse(processed.contains("import demo.Helper;"));
        assertFalse(processed.contains("import java.lang.String;"));
        assertTrue(processed.contains("import demo.nested.Other;"));
        assertTrue(processed.contains("import java.util.List;"));
    }

    @Test
    void removesParameterArrayCasts() {
        String processed = new SourcePostProcessor().process(
                "public static void main(String[] args) {\n"
                        + "        run((String[])args);\n"
                        + "}\n");

        assertTrue(processed.contains("run(args);"));
        assertFalse(processed.contains("(String[])args"));
    }

    @Test
    void removesRepeatedValidationAnnotationsOnOneParameter() {
        String processed = new SourcePostProcessor().process(
                "public Result<UserBankCardDTO> queryById(@PathVariable(value=\"id\") "
                        + "@Valid @NotNull(message=\"id cannot be empty\") "
                        + "@Valid @NotNull(message=\"id cannot be empty\") Long id) {\n"
                        + "    return Result.ok(id);\n"
                        + "}\n");

        assertTrue(processed.contains("@Valid @NotNull(message=\"id cannot be empty\") Long id"));
        assertFalse(processed.contains("@Valid @NotNull(message=\"id cannot be empty\") @Valid"));
    }

    @Test
    void restoresLambdaQueryWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "LambdaQueryWrapper queryWrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)new "
                        + "LambdaQueryWrapper().eq(User::getUid, uid)).orderByAsc(User::getUid);\n"
                        + "List list = this.userService.list((Wrapper)queryWrapper);\n"
                        + "this.userService.clean(list.stream().map(User::getUid).collect(Collectors.toSet()));\n");

        assertTrue(processed.contains("LambdaQueryWrapper<User> queryWrapper"));
        assertTrue(processed.contains("(LambdaQueryWrapper<User>)new LambdaQueryWrapper<User>()"));
        assertTrue(processed.contains("List<User> list = this.userService.list((Wrapper)queryWrapper);"));
    }

    @Test
    void restoresLambdaUpdateWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "this.sysUserMapper.update(null, (Wrapper)((LambdaUpdateWrapper)new LambdaUpdateWrapper()"
                        + ".eq(SysUser::getUserId, req.getUserId())).set(SysUser::getDeviceLimitEnabled, "
                        + "req.getDeviceLimitEnabled()));\n");

        assertTrue(processed.contains("(LambdaUpdateWrapper<SysUser>)new LambdaUpdateWrapper<SysUser>()"));
    }

    @Test
    void restoresLambdaUpdateChainWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "((LambdaUpdateChainWrapper)((LambdaUpdateChainWrapper)this.userCoinRecordService.lambdaUpdate()"
                        + ".set(UserCoinRecord::getUid, financeUid)).in(UserCoinRecord::getTargetId, "
                        + "sourceRedPacketIds)).update();\n");

        assertTrue(processed.contains("(LambdaUpdateChainWrapper<UserCoinRecord>)this.userCoinRecordService"
                + ".lambdaUpdate()"));
        assertFalse(processed.contains("(LambdaUpdateChainWrapper)this.userCoinRecordService.lambdaUpdate()"));
    }

    @Test
    void unwrapsSingleElementRedisSetOperationArrays() {
        String processed = new SourcePostProcessor().process(
                "userTemplate.opsForSet().add(\"user:change:device:switch\", "
                        + "(Object[])new String[]{UserChangeDeviceSwitchType.getCode((String)parentCode).getCode()});\n"
                        + "userTemplate.opsForSet().remove(\"user:change:device:switch\", "
                        + "new Object[]{UserChangeDeviceSwitchType.getCode((String)parentCode).getCode()});\n");

        assertTrue(processed.contains("opsForSet().add(\"user:change:device:switch\", "
                + "UserChangeDeviceSwitchType.getCode((String)parentCode).getCode())"));
        assertTrue(processed.contains("opsForSet().remove(\"user:change:device:switch\", "
                + "UserChangeDeviceSwitchType.getCode((String)parentCode).getCode())"));
        assertFalse(processed.contains("new String[]"));
        assertFalse(processed.contains("new Object[]"));
    }

    @Test
    void scopesLambdaQueryWrapperTypesForRepeatedVariableNames() {
        String processed = new SourcePostProcessor().process(
                "while (true) {\n"
                        + "    LambdaQueryWrapper queryWrapper;\n"
                        + "    List userList;\n"
                        + "    if (empty((Collection)(userList = this.userService.list((Wrapper)(queryWrapper = "
                        + "(LambdaQueryWrapper)new LambdaQueryWrapper().gt(User::getUid, startUid))))) break;\n"
                        + "}\n"
                        + "while (true) {\n"
                        + "    LambdaQueryWrapper queryWrapper;\n"
                        + "    List descriptionList;\n"
                        + "    if (empty((Collection)(descriptionList = this.userDescriptionService.list((Wrapper)(queryWrapper = "
                        + "(LambdaQueryWrapper)new LambdaQueryWrapper().gt(UserDescription::getId, startId))))) break;\n"
                        + "}\n");

        assertTrue(processed.contains("LambdaQueryWrapper<User> queryWrapper;"));
        assertTrue(processed.contains("List<User> userList;"));
        assertTrue(processed.contains("LambdaQueryWrapper<UserDescription> queryWrapper;"));
        assertTrue(processed.contains("List<UserDescription> descriptionList;"));
        assertFalse(processed.contains("LambdaQueryWrapper<UserDescription> queryWrapper;\n"
                + "    List<UserDescription> userList;"));
    }

    @Test
    void restoresPageInfoElementTypeFromRowListStreamMethodReference() {
        String processed = new SourcePostProcessor().process(
                "PageInfo resp;\n"
                        + "if (!empty((resp = this.api.query(req)).getRowList())) {\n"
                        + "    List uids = resp.getRowList().stream().map(AdOrderPageResp::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<AdOrderPageResp> resp;"));
    }

    @Test
    void restoresLocalTypeFromGenericMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public void run(Long id) {\n"
                        + "    Map userMap = this.getUserMap(null, Collections.singletonList(id));\n"
                        + "    String account = userMap.getOrDefault(id, \"\");\n"
                        + "}\n"
                        + "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Maps.newHashMap();\n"
                        + "}\n");

        assertTrue(processed.contains("Map<Long, String> userMap = this.getUserMap"));
    }

    @Test
    void restoresPageDataAndListTypesFromMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public Result<PageData<GroupsMemberListDTO>> pageInfo() {\n"
                        + "    PageData pageData = this.groupMemberService.queryByPage(req, pageRequest);\n"
                        + "    List groupsMemberListDTOList = pageData.getList();\n"
                        + "    List uids = groupsMemberListDTOList.stream().map(GroupsMemberListDTO::getUid).collect(Collectors.toList());\n"
                        + "    return this.handleResult(pageData);\n"
                        + "}\n");

        assertTrue(processed.contains("PageData<GroupsMemberListDTO> pageData"));
        assertTrue(processed.contains("List<GroupsMemberListDTO> groupsMemberListDTOList = pageData.getList();"));
    }

    @Test
    void leavesPageDataRawWhenMappedToDifferentReturnDto() {
        String processed = new SourcePostProcessor().process(
                "public Result<PageData<GroupsMemberListV2DTO>> getPage() {\n"
                        + "    PageData pageDate = this.groupMemberService.queryByPage(req, pageRequest);\n"
                        + "    return this.handleResult(new PageData(GroupMemberMapstruct.INSTANCE.toList(pageDate.getList()), "
                        + "pageDate.getTotal(), pageDate.getCurrent(), pageDate.getLimit(), pageDate.getPages()));\n"
                        + "}\n");

        assertTrue(processed.contains("PageData pageDate = this.groupMemberService.queryByPage"));
        assertFalse(processed.contains("PageData<GroupsMemberListV2DTO> pageDate"));
    }

    @Test
    void restoresLambdaQueryChainWrapperEntityTypeFromMethodReferences() {
        String processed = new SourcePostProcessor().process(
                "return ((LambdaQueryChainWrapper)((LambdaQueryChainWrapper)this.contactsService.lambdaQuery()"
                        + ".eq(Contacts::getUid, uid)).eq(Contacts::getStatus, 1)).list().stream()"
                        + ".map(Contacts::getFriendUid).collect(Collectors.toSet());\n");

        assertTrue(processed.contains("(LambdaQueryChainWrapper<Contacts>)this.contactsService.lambdaQuery()"));
        assertTrue(processed.contains("(LambdaQueryChainWrapper<Contacts>)((LambdaQueryChainWrapper<Contacts>)"));
    }

    @Test
    void alignsStreamMethodReferenceOwnerWithListElementType() {
        String processed = new SourcePostProcessor().process(
                "List<GroupsMemberListDTO> groupsMemberListDTOList = pageData.getList();\n"
                        + "List uids = groupsMemberListDTOList.stream().map(GroupMember::getUid).collect(Collectors.toList());\n");

        assertTrue(processed.contains("groupsMemberListDTOList.stream().map(GroupsMemberListDTO::getUid)"));
    }

    @Test
    void restoresStringSplitArraysFromCharSequenceArrays() {
        String processed = new SourcePostProcessor().process(
                "CharSequence[] picArray = descriptionPic.split(\",\", -1);\n"
                        + "String oldPic = picArray[i];\n");

        assertTrue(processed.contains("String[] picArray = descriptionPic.split(\",\", -1);"));
    }

    @Test
    void restoresStringListLocalsFromArraysAndGuavaListFactories() {
        String processed = new SourcePostProcessor().process(
                "ArrayList limitsKeys;\n"
                        + "limitsKeys = Arrays.asList(request.getParameter(\"authorityKey\"));\n"
                        + "limitsKeys = Lists.newArrayList((Object[])annotation.value());\n"
                        + "Lists.newArrayList((Iterable)limitsKeys).stream().map(arg -> service.getGoogleVerify(arg));\n");

        assertTrue(processed.contains("java.util.List<String> limitsKeys;"));
        assertTrue(processed.contains("Lists.newArrayList(annotation.value())"));
        assertTrue(processed.contains("Lists.newArrayList(limitsKeys).stream()"));
    }

    @Test
    void addsImmutableMapBuilderTypeArgumentsFromMapReturnType() {
        String processed = new SourcePostProcessor().process(
                "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Optional.ofNullable(user).map(e -> ImmutableMap.builder()"
                        + ".put(e.getUid(), e.getIdentify()).build()).orElseGet(() -> Maps.newHashMap());\n"
                        + "}\n");

        assertTrue(processed.contains("ImmutableMap.<Long, String>builder()"));
    }

    @Test
    void castsImmutableMapBuilderLambdaToMapReturnType() {
        String processed = new SourcePostProcessor().process(
                "private Map<Long, String> getUserMap(User user, List<Long> uidList) {\n"
                        + "    return Optional.ofNullable(user).map(e -> ImmutableMap.builder()"
                        + ".put(e.getUid(), e.getIdentify()).build()).orElseGet(() -> this.findUserMap(uidList));\n"
                        + "}\n");

        assertTrue(processed.contains("map(e -> (Map<Long, String>)ImmutableMap.<Long, String>builder()"));
    }

    @Test
    void restoresGenericReturnLocalTypeFromQualifiedCall() {
        String processed = new SourcePostProcessor().process(
                "public static void sendStringByUid(Long uid) {\n"
                        + "    Set userSocketSessionAll = WebSocketServer.getWebSocketSessionByUid((Long)uid);\n"
                        + "}\n"
                        + "private static Set<WebSocketSession> getWebSocketSessionByUid(Long uid) {\n"
                        + "    return uidToSessionMap.get(uid);\n"
                        + "}\n");

        assertTrue(processed.contains("Set<WebSocketSession> userSocketSessionAll = "
                + "WebSocketServer.getWebSocketSessionByUid"));
    }

    @Test
    void addsRawCollectionElementTypeWhenEnhancedForUsesTypedElement() {
        String processed = new SourcePostProcessor().process(
                "HashSet sessionsCopy = new HashSet(userSocketSessionAll);\n"
                        + "for (WebSocketSession socketSession : sessionsCopy) {\n"
                        + "}\n");

        assertTrue(processed.contains("HashSet<WebSocketSession> sessionsCopy = new HashSet<WebSocketSession>"));
        assertTrue(processed.contains("for (WebSocketSession socketSession : sessionsCopy)"));
    }

    @Test
    void restoresRawListElementTypeFromStreamMethodReference() {
        String processed = new SourcePostProcessor().process(
                "List list = this.redPacketService.getRedPacketList(req);\n"
                        + "Set sourceRedPacketIds = list.stream().map(RedPacket::getId).collect(Collectors.toSet());\n");

        assertTrue(processed.contains("List<RedPacket> list = this.redPacketService.getRedPacketList(req);"));
    }

    @Test
    void doesNotInferRawListElementTypeFromLaterMethodWithSameVariableName() {
        String processed = new SourcePostProcessor().process(
                "public boolean removeDuplicateReg() {\n"
                        + "    List list = this.accountService.listDuplicateReg();\n"
                        + "    for (Map accountMap : list) {}\n"
                        + "    return true;\n"
                        + "}\n"
                        + "private void disabledUser(List<User> list) {\n"
                        + "    List uidList = list.stream().map(User::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertFalse(processed.contains("List<User> list = this.accountService.listDuplicateReg();"));
    }

    @Test
    void restoresPageInfoLocalTypeFromPageDataMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public PageData<GroupObserverMemberAuditPageResp> getObserverList() {\n"
                        + "    PageInfo resp = this.groupObserverMemberApi.getObserverList(req);\n"
                        + "    resp.getRowList().forEach(item -> item.getGroupObserverMemberRespList());\n"
                        + "    return new PageData(resp.getRowList(), resp.getTotal());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo<GroupObserverMemberAuditPageResp> resp"));
    }

    @Test
    void leavesPageInfoRawWhenRowsAreMappedToReturnDto() {
        String processed = new SourcePostProcessor().process(
                "public PageData<AssistUserResp> assistUserPage() {\n"
                        + "    PageInfo resp = this.assistUserInnerInnerApi.queryPage(req);\n"
                        + "    return new PageData(AssistUserMapstruct.INSTANCE.toResp(resp.getRowList()), "
                        + "resp.getTotal(), resp.getPageNum(), resp.getPageSize());\n"
                        + "}\n");

        assertTrue(processed.contains("PageInfo resp = this.assistUserInnerInnerApi.queryPage(req);"));
        assertFalse(processed.contains("PageInfo<AssistUserResp> resp"));
    }

    @Test
    void restoresListLocalTypeFromListMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "public List<GroupObserverMemberPageResp> getGroupInfoByIds() {\n"
                        + "    List resp = this.groupObserverMemberApi.getGroupInfoByIds(req);\n"
                        + "    resp.forEach(item -> item.setGroupName(item.getGroupName()));\n"
                        + "    return resp;\n"
                        + "}\n");

        assertTrue(processed.contains("List<GroupObserverMemberPageResp> resp"));
    }

    @Test
    void restoresGenericListMethodTypeVariableLocals() {
        String processed = new SourcePostProcessor().process(
                "public static <S, T> List<T> copyListProperties(List<S> sources, Supplier<T> target) {\n"
                        + "    ArrayList<Object> list = new ArrayList<Object>(sources.size());\n"
                        + "    for (S source : sources) {\n"
                        + "        Object t = BeanCopyUtils.copy(source, target);\n"
                        + "        list.add(t);\n"
                        + "    }\n"
                        + "    return list;\n"
                        + "}\n");

        assertTrue(processed.contains("ArrayList<T> list = new ArrayList<T>(sources.size());"));
        assertTrue(processed.contains("T t = BeanCopyUtils.copy(source, target);"));
    }

    @Test
    void scopesStreamMethodReferenceOwnerAlignmentToCurrentMethod() {
        String processed = new SourcePostProcessor().process(
                "public void first() {\n"
                        + "    List<RechargePointsPageDTO> respList = mapper.toRechargeList(resp.getRowList());\n"
                        + "    List uids = respList.stream().map(ConsumerPageDTO::getUid).collect(Collectors.toList());\n"
                        + "}\n"
                        + "public void second() {\n"
                        + "    List<ConsumerPageDTO> respList = mapper.toConsumerList(resp.getRowList());\n"
                        + "    List uids = respList.stream().map(RechargePointsPageDTO::getUid).collect(Collectors.toList());\n"
                        + "}\n");

        assertTrue(processed.contains("respList.stream().map(RechargePointsPageDTO::getUid)"));
        assertTrue(processed.contains("respList.stream().map(ConsumerPageDTO::getUid)"));
    }

    @Test
    void preservesStaticParseMethodReferencesWhenListElementTypeIsDifferent() {
        String processed = new SourcePostProcessor().process(
                "List<String> list = Arrays.stream(uids.split(\",\")).toList();\n"
                        + "List uidList = list.stream().map(Long::parseLong).collect(Collectors.toList());\n");

        assertTrue(processed.contains("list.stream().map(Long::parseLong)"));
        assertFalse(processed.contains("String::parseLong"));
    }

    @Test
    void addsListElementTypeWhenEnhancedForUsesTypedElement() {
        String processed = new SourcePostProcessor().process(
                "List findPetTypes = this.types.findPetTypes();\n"
                        + "        for (PetType type : findPetTypes) {\n"
                        + "        }\n");

        assertTrue(processed.contains("List<PetType> findPetTypes = this.types.findPetTypes();"));
        assertTrue(processed.contains("for (PetType type : findPetTypes)"));
    }

    @Test
    void addsOptionalElementTypeAndRemovesOrElseThrowCast() {
        String processed = new SourcePostProcessor().process(
                "Optional optionalOwner = this.owners.findById(Integer.valueOf(ownerId));\n"
                        + "        Owner owner = (Owner)optionalOwner.orElseThrow(() -> new IllegalArgumentException(\"missing\"));\n");

        assertTrue(processed.contains("Optional<Owner> optionalOwner = this.owners.findById(Integer.valueOf(ownerId));"));
        assertTrue(processed.contains("Owner owner = optionalOwner.orElseThrow"));
        assertFalse(processed.contains("(Owner)optionalOwner"));
    }

    @Test
    void replacesUnavailableAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "private static final Runnable RUNNABLE = new /* Unavailable Anonymous Inner Class!! */;\n");

        assertTrue(processed.contains("RUNNABLE = null;"));
        assertFalse(processed.contains("Unavailable Anonymous Inner Class"));
    }

    @Test
    void replacesDuplicateAnonymousInnerClassPlaceholder() {
        String processed = new SourcePostProcessor().process(
                "worker.execute(new /* invalid duplicate definition of identical inner class */);\n");

        assertTrue(processed.contains("worker.execute(null);"));
        assertFalse(processed.contains("invalid duplicate definition"));
    }

    @Test
    void preservesSyntheticSwitchMapReferenceInsteadOfChangingCaseSemantics() {
        String processed = new SourcePostProcessor().process(
                "switch (1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]) {\n"
                        + "    case 1: break;\n"
                        + "}\n");

        assertTrue(processed.contains("1.$SwitchMap$com$example$Mode[value.getMode().ordinal()]"));
        assertFalse(processed.contains("switch (value.getMode().ordinal())"));
    }

    @Test
    void replacesNumericAnonymousClassFragments() {
        String processed = new SourcePostProcessor().process(
                "    private static final Runnable R = new 1();\n"
                        + "    private static final Handle H = new LocalPoolHandle((Pool) null, (1) null);\n"
                        + "    void add() {\n"
                        + "        2 strategy = null;\n"
                        + "    }\n");

        assertTrue(processed.contains("R = null;"));
        assertTrue(processed.contains("new LocalPoolHandle((Pool) null, null)"));
        assertTrue(processed.contains("Object strategy = null;"));
        assertFalse(processed.contains("new 1("));
        assertFalse(processed.contains("(1) null"));
    }

    @Test
    void replacesCfrVoidTemporaryLocalDeclarations() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "  void run() {\n"
                        + "    void var15_20;\n"
                        + "    System.out.println(1);\n"
                        + "  }\n"
                        + "}\n",
                "Sample");

        assertFalse(processed.contains("void var15_20;"));
        assertTrue(processed.contains("Object var15_20 = null;"));
    }

    @Test
    void removesDecompilerDiagnosticComments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "  /*\n"
                        + "   * WARNING - void declaration\n"
                        + "   * Enabled force condition propagation\n"
                        + "   * Lifted jumps to return sites\n"
                        + "   */\n"
                        + "  Object run() {\n"
                        + "    synchronized (this) {\n"
                        + "      // ** MonitorExit[var0] (shouldn't be in output)\n"
                        + "      return value();\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n",
                "Sample");

        assertFalse(processed.contains("WARNING - void declaration"));
        assertFalse(processed.contains("Enabled force condition propagation"));
        assertFalse(processed.contains("Lifted jumps to return sites"));
        assertFalse(processed.contains("MonitorExit"));
        assertTrue(processed.contains("return value();"));
    }

    @Test
    void shortensQualifiedInnerClassInstanceCreation() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Ansi ansi, Handler handler, Method method) {\n"
                        + "        Ansi.Text text = ansi.new Ansi.Text(0);\n"
                        + "        Handler.Binding binding = handler.new Handler.Binding(method);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ansi.new Text(0);"));
        assertTrue(processed.contains("handler.new Binding(method);"));
        assertFalse(processed.contains("ansi.new Ansi.Text"));
        assertFalse(processed.contains("handler.new Handler.Binding"));
    }

    @Test
    void infersNumericGenericPlaceholderFromEnhancedForElementType() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run() {\n"
                        + "        ArrayList<1> bookKeeping = new ArrayList<1>();\n"
                        + "        bookKeeping.add(new Runnable() { public void run() {} });\n"
                        + "        for (Runnable runnable : bookKeeping) {\n"
                        + "            runnable.run();\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("ArrayList<Runnable> bookKeeping = new ArrayList<Runnable>();"));
        assertFalse(processed.contains("ArrayList<1>"));
    }

    @Test
    void addsObjectElementTypeToRawListCastDeclarations() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    <T> T call(Command cmd) {\n"
                        + "        List results = (List)cmd.parse();\n"
                        + "        return Sample.firstElement(results);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Object> results = (List<Object>)cmd.parse();"));
        assertFalse(processed.contains("List results = (List)"));
    }

    @Test
    void preservesRawListCastDeclarationsWhenElementTypeIsUnknown() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Map<Object, ArrayList<String>> done, Object key) {\n"
                        + "        List aliases = (List)done.get(key);\n"
                        + "        aliases.add(\"name\");\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List aliases = (List)done.get(key);"));
        assertFalse(processed.contains("List<Object> aliases"));
    }

    @Test
    void widensExecutionExceptionCauseLocalsToException() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(ExecutionException ex) {\n"
                        + "        ExecutionException cause = ex.getCause() instanceof Exception ? (Exception)ex.getCause() : ex;\n"
                        + "        handle(cause);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "Exception cause = ex.getCause() instanceof Exception ? (Exception)ex.getCause() : ex;"));
        assertFalse(processed.contains("ExecutionException cause ="));
    }

    @Test
    void replacesAnsiTextSyntheticOuterReference() {
        String processed = new SourcePostProcessor().process(
                "class Help {\n"
                        + "    static ColorScheme defaultColorScheme(Ansi ansi) { return null; }\n"
                        + "    enum Ansi {\n"
                        + "        ON;\n"
                        + "        public class Text {\n"
                        + "            public Text(int maxLength) {\n"
                        + "                this(maxLength, Help.defaultColorScheme(this$0));\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("Help.defaultColorScheme(Ansi.this)"));
        assertFalse(processed.contains("this$0"));
    }

    @Test
    void hoistsForLoopCounterWhenReferencedByLaterLoop() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Capacity capacity) {\n"
                        + "        int i;\n"
                        + "        for (int done = 1; done < capacity.min; ++done) {\n"
                        + "            use(done);\n"
                        + "        }\n"
                        + "        for (i = done; i < capacity.max; ++i) {\n"
                        + "            use(i);\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("int done;\n        for (done = 1; done < capacity.min; ++done)"));
        assertFalse(processed.contains("for (int done = 1;"));
    }

    @Test
    void removesUndefinedGenericArrayCastFromToArrayArgument() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Text[][] run(ArrayList<Text[]> result) {\n"
                        + "        return (Text[][])result.toArray((T[])new Text[result.size()][]);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("return (Text[][])result.toArray(new Text[result.size()][]);"));
        assertFalse(processed.contains("(T[])new"));
    }

    @Test
    void castsClassWildcardForEnumGenericFactories() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Object run(Class<?> type, Class<?>[] elementType, String value) {\n"
                        + "        EnumSet<?> enumSet = EnumSet.noneOf(elementType[0]);\n"
                        + "        if (value == null) return enumSet;\n"
                        + "        return Enum.valueOf(type, value);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("EnumSet.noneOf((Class) elementType[0])"));
        assertTrue(processed.contains("return (Collection<Object>) enumSet;"));
        assertTrue(processed.contains("Enum.valueOf((Class) type, value)"));
    }

    @Test
    void restoresGenericDefaultExceptionHandlerConstruction() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    public <R> R parseWithHandler(IParseResultHandler2<R> handler, String[] args) {\n"
                        + "        return this.parseWithHandlers(handler, new DefaultExceptionHandler(), args);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("new DefaultExceptionHandler<R>()"));
        assertFalse(processed.contains("new DefaultExceptionHandler(),"));
    }

    @Test
    void widensRegexTransformerConditionalsToInterfaceType() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Option option) {\n"
                        + "        RegexTransformer trans = option.commandSpec == null ? RegexTransformer.createDefault() : option.commandSpec.negatableOptionTransformer();\n"
                        + "        trans.makeSynopsis(option.shortestName(), option.commandSpec);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("INegatableOptionTransformer trans = option.commandSpec == null"));
        assertFalse(processed.contains("RegexTransformer trans ="));
    }

    @Test
    void restoresPositionalParamSpecListLocals() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Model.ArgSpec argSpec, List<Model.PositionalParamSpec> positionals) {\n"
                        + "        List<Object> missingList = Collections.emptyList();\n"
                        + "        if (argSpec.isPositional()) {\n"
                        + "            missingList = positionals.subList(0, positionals.size());\n"
                        + "        }\n"
                        + "        createMissingParameterMessage(argSpec, missingList);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("List<Model.PositionalParamSpec> missingList = Collections.emptyList();"));
        assertFalse(processed.contains("List<Object> missingList"));
    }

    @Test
    void restoresWildcardClassLocalsForReflectionTraversal() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(UserObject userObject) {\n"
                        + "        Class<Object> cls;\n"
                        + "        Class<Object> clazz = cls = userObject.getType();\n"
                        + "        Stack<Class> hierarchy = new Stack<Class>();\n"
                        + "        for (cls = userObject.getType(); cls != null; cls = cls.getSuperclass()) {\n"
                        + "            hierarchy.add(cls);\n"
                        + "        }\n"
                        + "        cls = (Class)hierarchy.pop();\n"
                        + "        Command cmd = cls.getAnnotation(Command.class);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("Class<?> cls;"));
        assertTrue(processed.contains("Class<?> clazz = cls = userObject.getType();"));
        assertTrue(processed.contains("Stack<Class<?>> hierarchy = new Stack<Class<?>>();"));
        assertTrue(processed.contains("cls = hierarchy.pop();"));
        assertFalse(processed.contains("Class<Object>"));
        assertFalse(processed.contains("(Class)hierarchy.pop()"));
    }

    @Test
    void castsWildcardClassArrayElementsAddedToRawClassLists() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(List<Class> result, Class<?>[] aux) {\n"
                        + "        result.add(aux[0]);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("result.add((Class) aux[0]);"));
        assertFalse(processed.contains("result.add(aux[0]);"));
    }

    @Test
    void restoresGenericReturnLocalTypeForReturnedObjectVariables() {
        String processed = new SourcePostProcessor().process(
                "class Sample<K, V> {\n"
                        + "    public V put(K key, V value) {\n"
                        + "        Object removedValue = this.targetMap.remove(key);\n"
                        + "        this.targetMap.put(key, value);\n"
                        + "        return removedValue;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("V removedValue = this.targetMap.remove(key);"));
        assertFalse(processed.contains("Object removedValue"));
    }

    @Test
    void splitsMapPutKeyAssignmentBeforeUse() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(Map<String, Integer> m, String sequence, int i, int degree) {\n"
                        + "        String gram;\n"
                        + "        m.put(gram, 1 + (m.containsKey(gram = sequence.substring(i, i + degree)) ? (Integer)m.get(gram) : 0));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("String gram = sequence.substring(i, i + degree);\n"
                + "        m.put(gram, 1 + (m.containsKey(gram) ? (Integer)m.get(gram) : 0));"));
        assertFalse(processed.contains("String gram;\n"));
    }

    @Test
    void restoresNestedStringConditionalAssignments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    String get(String key, CommandSpec commandSpec) {\n"
                        + "        String result;\n"
                        + "        String string = \"COMMAND-NAME\".equals(key) ? commandSpec.name() : (result = \"ROOT-COMMAND-NAME\".equals(key) ? commandSpec.root().name() : null);\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("String result = \"COMMAND-NAME\".equals(key) ? commandSpec.name() : (\"ROOT-COMMAND-NAME\".equals(key) ? commandSpec.root().name() : null);"));
        assertFalse(processed.contains("String string ="));
    }

    @Test
    void restoresNestedIntConditionalAssignments() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    int compareTo(Range other) {\n"
                        + "        int result;\n"
                        + "        int n = this.anchor() < other.anchor() ? -1 : (result = this.anchor() == other.anchor() ? 0 : 1);\n"
                        + "        if (result == 0) {\n"
                        + "            int n2 = this.max < other.max ? -1 : (result = this.max == other.max ? 0 : 1);\n"
                        + "        }\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("int result = this.anchor() < other.anchor() ? -1 : (this.anchor() == other.anchor() ? 0 : 1);"));
        assertTrue(processed.contains("result = this.max < other.max ? -1 : (this.max == other.max ? 0 : 1);"));
        assertFalse(processed.contains("int n ="));
        assertFalse(processed.contains("int n2 ="));
    }

    @Test
    void restoresNestedIntConditionalAssignmentsAfterGuardBlocks() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    int compareTo(Range other) {\n"
                        + "        int result;\n"
                        + "        if (same(other)) {\n"
                        + "            return 0;\n"
                        + "        }\n"
                        + "        int n = this.anchor() < other.anchor() ? -1 : (result = this.anchor() == other.anchor() ? 0 : 1);\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("result = this.anchor() < other.anchor() ? -1 : (this.anchor() == other.anchor() ? 0 : 1);"));
        assertFalse(processed.contains("int n ="));
    }

    @Test
    void removesWildcardBoundsFromLambdaParameterList() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void run(ExtendedAttributes<K, V> attributes) {\n"
                        + "        attributes.forEach((? super K extendedAttributeKey, ? super V value) -> {\n"
                        + "            use(extendedAttributeKey, value);\n"
                        + "        });\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains("attributes.forEach((extendedAttributeKey, value) -> {"));
        assertFalse(processed.contains("? super"));
    }

    @Test
    void balancesNullArgumentStatementsAfterAnonymousPlaceholderReplacement() {
        String processed = new SourcePostProcessor().process(
                "seedGeneratorThread.setUncaughtExceptionHandler(null;\n"
                        + "Object ret = AccessController.doPrivileged((PrivilegedAction<Object>) null;\n");

        assertTrue(processed.contains("setUncaughtExceptionHandler(null);"));
        assertTrue(processed.contains("doPrivileged((PrivilegedAction<Object>) null);"));
    }

    @Test
    void castsDoPrivilegedMethodReferenceToPrivilegedAction() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    ClassLoader get() {\n"
                        + "        return AccessController.doPrivileged(Sample::directGetContextClassLoader);\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) Sample::directGetContextClassLoader)"));
    }

    @Test
    void castsDoPrivilegedLambdaToPrivilegedActionUsingEnclosingReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static ClassLoader getContextClassLoaderInternal() throws Exception {\n"
                        + "        return AccessController.doPrivileged(() -> Sample.directGetContextClassLoader());\n"
                        + "    }\n"
                        + "    static ClassLoader directGetContextClassLoader() { return null; }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> Sample.directGetContextClassLoader())"));
    }

    @Test
    void castsDoPrivilegedInsideIfBlockUsingEnclosingMethodReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.nio.file.Files;\n"
                        + "import java.nio.file.Path;\n"
                        + "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    static Boolean isSymbolicLink(Path file) {\n"
                        + "        if (System.getSecurityManager() == null) {\n"
                        + "            return Files.isSymbolicLink(file);\n"
                        + "        }\n"
                        + "        return AccessController.doPrivileged(() -> Files.isSymbolicLink(file));\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> Files.isSymbolicLink(file))"));
        assertFalse(processed.contains("PrivilegedAction<if>"));
    }

    @Test
    void castsDoPrivilegedInsideGenericMethodUsingGenericReturnType() {
        String processed = new SourcePostProcessor().process(
                "import java.security.AccessController;\n"
                        + "import java.security.PrivilegedAction;\n"
                        + "class Sample {\n"
                        + "    private static <T> T doPrivileged(PrivilegedAction<T> action) {\n"
                        + "        return AccessController.doPrivileged(action);\n"
                        + "    }\n"
                        + "}\n");

        assertTrue(processed.contains(
                "AccessController.doPrivileged((PrivilegedAction<T>) action)"));
        assertFalse(processed.contains("PrivilegedAction<<T> T>"));
    }

    @Test
    void removesUnreachableBreakAfterInfiniteLoopThatAlreadyReturnsOrThrows() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    Object row() {\n"
                        + "        while (true) {\n"
                        + "            if (done()) {\n"
                        + "                return new Object();\n"
                        + "            }\n"
                        + "            throw new RuntimeException();\n"
                        + "        }\n"
                        + "        break;\n"
                        + "    }\n"
                        + "}\n");

        assertFalse(processed.contains("        break;\n"));
        assertTrue(processed.contains("while (true)"));
    }

    @Test
    void restoresSyntheticEnumSwitchExpressionFromSwitchMap() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    String describe(GroupMemberLimitMsgType type) {\n"
                        + "        return switch (1.$SwitchMap$com$ochat$group$enums$GroupMemberLimitMsgType[type.ordinal()]) {\n"
                        + "            default -> throw new MatchException(null, null);\n"
                        + "            case 1 -> \"expire\";\n"
                        + "            case 2 -> \"balance\";\n"
                        + "            case 3 -> \"renew\";\n"
                        + "        };\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of("$SwitchMap$com$ochat$group$enums$GroupMemberLimitMsgType",
                        Map.of(1, "EXPIRATION_REMINDER", 2, "BALANCE_INSUFFICIENT", 3, "AUTO_RENEW_SUCCESS")));

        assertTrue(processed.contains("return switch (type)"));
        assertTrue(processed.contains("case EXPIRATION_REMINDER -> \"expire\";"));
        assertTrue(processed.contains("case BALANCE_INSUFFICIENT -> \"balance\";"));
        assertTrue(processed.contains("case AUTO_RENEW_SUCCESS -> \"renew\";"));
        assertFalse(processed.contains("1.$SwitchMap"));
        assertFalse(processed.contains("case 1 ->"));
    }

    @Test
    void restoresSyntheticEnumSwitchStatementUsingOriginalCaseOrder() {
        String processed = new SourcePostProcessor().process(
                "class Sample {\n"
                        + "    void send(NoticeSendType type) {\n"
                        + "        switch (1.$SwitchMap$otc$admin$share$enums$NoticeSendType[type.ordinal()]) {\n"
                        + "            case 1:\n"
                        + "                sendAll();\n"
                        + "                break;\n"
                        + "            case 2:\n"
                        + "                sendSelected();\n"
                        + "                break;\n"
                        + "            case 5:\n"
                        + "                sendNonAuth();\n"
                        + "                break;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n",
                "demo.Sample",
                Map.of("$SwitchMap$otc$admin$share$enums$NoticeSendType",
                        Map.of(1, "ALL", 2, "SELECTED_USER", 5, "NON_AUTH")));

        assertTrue(processed.contains("switch (type)"));
        assertTrue(processed.contains("case ALL:"));
        assertTrue(processed.contains("case SELECTED_USER:"));
        assertTrue(processed.contains("case NON_AUTH:"));
        assertFalse(processed.contains("case 1:"));
        assertFalse(processed.contains("case 2:"));
        assertFalse(processed.contains("case 5:"));
    }
}
