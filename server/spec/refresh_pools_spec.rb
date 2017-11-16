require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'


describe 'Refresh Pools' do
  include CandlepinMethods
  include AttributeHelper
  include CertificateMethods
  include VirtHelper

  before(:each) do
    skip("candlepin running in standalone mode") unless is_hosted?
  end

  it 'creates a valid job' do
    owner = create_owner random_string('test_owner')

    status = @cp.refresh_pools(owner['key'], true)
    status.state.should eq('CREATED')

    # URI returned is valid - use post to clean up
    @cp.post(status.statusPath).state.should_not be_nil
  end

  it 'contains the proper return value' do
    test_owner = random_string('test_owner')
    owner = create_owner test_owner
    result = @cp.refresh_pools(owner['key'])

    if !is_hosted?
      result.should be_nil
    else
      result.should eq("Pools refreshed for owner #{test_owner}")
    end
  end

  it 'creates the correct number of pools' do
    owner = create_owner random_string('some-owner')

    # Create 6 subscriptions to different products
    6.times do |i|
      product = create_upstream_product(random_string("product-#{i}"))
      create_upstream_subscription(random_string("sub-#{i}"), owner['key'], product.id)
    end

    @cp.refresh_pools(owner['key'])
    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

  it 'dispatches the correct number of events' do
    owner = create_owner random_string('some-owner')

    # Create 6 subscriptions to different products
    6.times do |i|
      product = create_upstream_product(random_string("product-#{i}"))
      create_upstream_subscription(random_string("sub-#{i}"), owner['key'], product.id)
    end

    @cp.refresh_pools(owner['key'])
    sleep 1

    events = @cp.list_owner_events(owner['key'])
    pool_created_events = events.find_all { |event| event['target'] == 'POOL' && event['type'] == 'CREATED'}
    pool_created_events.size.should eq(6)
  end

  it 'detects changes in provided products' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'))
    provided = []

    3.times do |i|
      provided << create_upstream_product(random_string("provided-#{i}"))
    end

    sub = create_upstream_subscription(random_string('test_sub'), owner_key, product.id, {
      :provided_products => provided[0..1]
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(2)

    provided_ids = provided[0..1].collect { |p| p.id }
    pools[0].providedProducts.each do |p|
      expect(provided_ids).to include(p.productId)
      provided_ids.delete(p.productId)
    end

    actual = pools[0].providedProducts.collect { |p| p.productId }
    expect(actual).to eq(provided[0..1].collect { |p| p.id })

    # Remove the old provided products and add a new one...
    sub.providedProducts = [provided[2]]
    update_upstream_subscription(sub.id, sub)

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(1)
    expect(pools[0].providedProducts[0].productId).to eq(provided[2].id)
  end

  it 'deletes expired subscriptions\' pools and entitlements' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, product.id, { :quantity => 500 })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))
    entitlements = consumer.consume_pool(pools.first.id, {:quantity => 1})
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    # Update subscription such that it's expired, then refresh. The entitlements should be removed.
    update_upstream_subscription(sub.id, {
      :start_date => Date.today - 20,
      :end_date => Date.today - 10
    })
    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools({:owner => owner.id})
    consumer = @cp.get_consumer(consumer.uuid)

    expect(pools.length).to eq(0)
    expect(consumer.entitlementCount).to eq(0)
  end

  it 'regenerates entitlements' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product1 = create_upstream_product(random_string('test_prod1'))
    product2 = create_upstream_product(random_string('test_prod2'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, product1.id)

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))
    entitlements = consumer.consume_pool(pools.first.id, {:quantity => 1})
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    old_cert = entitlement['certificates'].first
    old_serial = old_cert['serial']['serial']

    # Update the subscription's product to trigger an entitlement regeneration
    update_upstream_subscription(sub.id, {
      :product => { :id => product2.id }
    })
    @cp.refresh_pools(owner_key)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    updated_ent = @cp.get_entitlement(entitlement['id'])
    new_cert = updated_ent['certificates'].first
    new_serial = new_cert['serial']['serial']

    expect(new_serial).to_not eq(old_serial)
  end

  it 'handle derived products being removed' do
    # 998317: is caused by refresh pools dying with an NPE
    # this happens when subscriptions no longer have
    # derived products resulting in a null during the refresh
    # which we didn't handle in all cases.
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    datacenter_product = create_upstream_product(random_string('dc_prod'), {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'multi-entitlement' => "yes"
      }
    })

    derived_product = create_upstream_product(random_string('derived_prod'), {
      :attributes => {
        :cores => 2,
        :sockets=>4
      }
    })

    eng_product = create_upstream_product(random_string('eng_prod'))

    sub = create_upstream_subscription(random_string('dc_sub'), owner_key, datacenter_product.id, {
      :quantity => 10,
      :derived_product => datacenter_product,
      :derived_provided_products => [derived_product]
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools.first).to have_key('derivedProvidedProducts')
    expect(pools.first.derivedProvidedProducts.length).to eq(1)

    update_upstream_subscription(sub.id, {
      :derived_product => nil,
      :derived_provided_products => []
    })

    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools :owner => owner.id, :product => datacenter_product.id
    expect(pools.length).to eq(1)
    expect(pools.first).to have_key('derivedProvidedProducts')
    expect(pools.first.derivedProvidedProducts.length).to eq(0)
  end

  it 'can migrate subscriptions' do
    owner_key1 = random_string('test_owner_1')
    owner_key2 = random_string('test_owner_2')
    owner1 = create_owner(owner_key1)
    owner2 = create_owner(owner_key2)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key1, product.id)

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(1)
    expect(pools2.length).to eq(0)

    # Update sub to be owned by the second owner
    update_upstream_subscription(sub.id, { :owner => { :key => owner_key2 }})

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(0)
    expect(pools2.length).to eq(1)
  end

  it 'removes pools from other owners when subscription is migrated' do
    owner_key1 = random_string('test_owner_1')
    owner_key2 = random_string('test_owner_2')
    owner1 = create_owner(owner_key1)
    owner2 = create_owner(owner_key2)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key1, product.id)

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(1)
    expect(pools2.length).to eq(0)

    # Update sub to be owned by the second owner
    update_upstream_subscription(sub.id, { :owner => { :key => owner_key2 }})

    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(0)
    expect(pools2.length).to eq(1)
  end

  # Testing bug #1150234:
  it 'can change attributes and revoke entitlements at same time' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('multient_prod'), {
      :attributes => {
        'multi-entitlement' => 'yes'
      }
    })

    sub = create_upstream_subscription(random_string('multient_sub'), owner_key, product.id, {
      :quantity => 2
    })

    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'))

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # We'll consume quantity 2, later we will reduce the pool to 1 forcing revokation of this entitlement
    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 2 })
    expect(entitlements.length).to eq(1)

    # FIXME: This seems like a bug. Our entitlement count here should be 1, right?
    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(2)

    # Add a new attribute to the product
    update_upstream_product(product.id, {
      :attributes => {
        'new_attrib' => 'new value',
        'multi-entitlement' => 'yes'
      }
    })

    # ...and reduce the quantity available on the subscription
    update_upstream_subscription(sub.id, {
      :quantity => 1
    })

    # Refresh pools for this org
    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the pool's product now contains the new attribute
    expect(pools.first).to have_key('productAttributes')

    attributes = normalize_attributes(pools.first.productAttributes)
    expect(attributes).to eq({
      'new_attrib' => 'new value',
      'multi-entitlement' => 'yes'
    })

    # Verify that the entitlement was revoked
    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(0)

    lambda do
      @cp.get_entitlement(entitlements[0].id)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'regenerates entitlements when content for an entitled pool changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :label => 'test_label', :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id)

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, product_id)

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the content exists in its initial state
    ds_content = @cp.get_content(owner_key, content_id)
    expect(ds_content).to_not be_nil
    expect(ds_content.label).to eq('test_label')

    # Consume the pool so we have an entitlement
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    ent_cert = entitlement['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Modify the content for this product/sub
    update_upstream_content(content_id, { :label => 'updated_label' })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the content change has been pulled down
    ds_content = @cp.get_content(owner_key, content_id)
    expect(ds_content).to_not be_nil
    expect(ds_content.label).to eq('updated_label')

    # Verify the entitlement cert has changed as a result
    updated_ent = @cp.get_entitlement(entitlement['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)
  end

  it 'regenerates entitlements when products for an entitled pool changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, { :name => 'test_prod' })

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, product_id)

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('test_prod')

    # Consume the pool so we have an entitlement
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    ent_cert = entitlement['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Modify the product for this sub
    update_upstream_product(product_id, { :name => 'updated_name' })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the product change has been pulled down
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('updated_name')

    # Verify the entitlement cert has changed as a result
    updated_ent = @cp.get_entitlement(entitlement['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)
  end

  it 'regenerates entitlements when required products change' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    sku_id1 = random_string('required_prod', true)
    sku_id2 = random_string('dependent_prod', true)
    sku_prod1 = create_upstream_product(sku_id1)
    sku_prod2 = create_upstream_product(sku_id2)

    eng_id1 = random_string(nil, true)
    eng_id2 = random_string(nil, true)
    eng_prod1 = create_upstream_product(eng_id1)
    eng_prod2 = create_upstream_product(eng_id2)

    content_id1 = random_string('test_content_1')
    content1 = create_upstream_content(content_id1, { :content_url => 'http://www.url.com/c1' })

    content_id2 = random_string('test_content_2')
    content2 = create_upstream_content(content_id2, { :content_url => 'http://www.url.com/c2' })

    content_id3 = random_string('test_content_3')
    content3 = create_upstream_content(content_id3, { :content_url => 'http://www.url.com/c3' })

    add_content_to_product_upstream(eng_id1, content_id1)
    add_content_to_product_upstream(eng_id2, content_id2)
    add_content_to_product_upstream(eng_id2, content_id3)

    sub_id1 = random_string('test_subscription_1')
    sub1 = create_upstream_subscription(sub_id1, owner_key, sku_id1, { :provided_products => [eng_prod1] })

    sub_id2 = random_string('test_subscription_2')
    sub2 = create_upstream_subscription(sub_id2, owner_key, sku_id2, { :provided_products => [eng_prod2] })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2)

    # Rearrange the pools if they're backward
    if pools.first.productId == sku_id2
      pools[0], pools[1] = pools[1], pools[0]
    end

    products = @cp.list_products_by_owner(owner_key)
    expect(products.length).to eq(4)

    content = @cp.list_content(owner_key)
    expect(content.length).to eq(3)

    # Consume both pools
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    pool_ents = []

    entitlements = consumer_client.consume_pool(pools[0].id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)
    pool_ents << entitlements.first

    entitlements = consumer_client.consume_pool(pools[1].id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)
    pool_ents << entitlements.first

    entitlements = @cp.list_entitlements({ :uuid => consumer_client.uuid })
    expect(entitlements.length).to eq(2)

    payload1 = extract_payload(pool_ents[0].certificates.first['cert'])
    payload2 = extract_payload(pool_ents[1].certificates.first['cert'])

    # Verify the entitlements contains the products and content
    expect(payload1).to have_key('products')
    expect(payload1.products.length).to eq(1)
    expect(payload1.products.first.id).to eq(eng_id1)
    expect(payload1.products.first).to have_key('content')
    expect(payload1.products.first.content.length).to eq(1)
    expect(payload1.products.first.content.first.id).to eq(content1.id)
    expect(payload1.products.first.content.first.path).to eq(content1.contentUrl)

    expect(payload2).to have_key('products')
    expect(payload2.products.length).to eq(1)
    expect(payload2.products.first).to have_key('content')
    expect(payload2.products.first.id).to eq(eng_id2)
    expect(payload2.products.first.content.length).to eq(2)

    if payload2.products.first.content[0].id == content2.id
      expect(payload2.products.first.content[0].id).to eq(content2.id)
      expect(payload2.products.first.content[0].path).to eq(content2.contentUrl)
      expect(payload2.products.first.content[1].id).to eq(content3.id)
      expect(payload2.products.first.content[1].path).to eq(content3.contentUrl)
    else
      expect(payload2.products.first.content[0].id).to eq(content3.id)
      expect(payload2.products.first.content[0].path).to eq(content3.contentUrl)
      expect(payload2.products.first.content[1].id).to eq(content2.id)
      expect(payload2.products.first.content[1].path).to eq(content2.contentUrl)
    end

    ent_cert = pool_ents[1]['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Add a dependent product to content2 for a product the consumer is entitled to
    update_upstream_content(content_id2, { :modified_product_ids => [eng_id1] })
    @cp.refresh_pools(owner_key)

    # Verify the content change has been pulled down
    content = @cp.get_content(owner_key, content_id2)
    expect(content.modifiedProductIds).to eq([eng_id1])

    # Verify the entitlement has been regenerated
    updated_ent = @cp.get_entitlement(pool_ents[1]['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)

    # Verify the content path is still present in the entitlement
    payload = extract_payload(updated_ent['certificates'].first['cert'])

    expect(payload).to have_key('products')
    expect(payload.products.length).to eq(1)
    expect(payload.products.first).to have_key('content')
    expect(payload.products.first.id).to eq(eng_id2)
    expect(payload.products.first.content.length).to eq(2)

    if payload.products.first.content[0].id == content2.id
      expect(payload.products.first.content[0].id).to eq(content2.id)
      expect(payload.products.first.content[0].path).to eq(content2.contentUrl)
      expect(payload.products.first.content[1].id).to eq(content3.id)
      expect(payload.products.first.content[1].path).to eq(content3.contentUrl)
    else
      expect(payload.products.first.content[0].id).to eq(content3.id)
      expect(payload.products.first.content[0].path).to eq(content3.contentUrl)
      expect(payload.products.first.content[1].id).to eq(content2.id)
      expect(payload.products.first.content[1].path).to eq(content2.contentUrl)
    end

    # Add a dependent product to content3 for a product the consumer is NOT entitled to
    update_upstream_content(content_id3, { :modified_product_ids => ['fake_pid'] })
    @cp.refresh_pools(owner_key)

    # Verify the content change has been pulled down
    content = @cp.get_content(owner_key, content_id3)
    expect(content.modifiedProductIds).to eq(['fake_pid'])

    # Verify the entitlement has been regenerated
    updated_ent = @cp.get_entitlement(pool_ents[1]['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)

    # Verify the content path is still present in the entitlement
    payload = extract_payload(updated_ent['certificates'].first['cert'])

    expect(payload).to have_key('products')
    expect(payload.products.length).to eq(1)
    expect(payload.products.first).to have_key('content')
    expect(payload.products.first.id).to eq(eng_id2)
    expect(payload.products.first.content.length).to eq(1)
    expect(payload.products.first.content.first.id).to eq(content2.id)
    expect(payload.products.first.content.first.path).to eq(content2.contentUrl)
  end

end
