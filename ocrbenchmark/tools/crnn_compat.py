"""Replace unsupported ConvInteger with zero-point-centered float Conv + int32 cast.
Retains quantized activation/weight values, scales and downstream graph. Float
accumulation is not asserted bit-exact for large sums; benchmark the derivative.
"""
import onnx
from onnx import helper, TensorProto, numpy_helper
import numpy as np
import onnxruntime as ort
def convert(data):
    model=onnx.load_from_string(data)
    graph=model.graph
    init={i.name:i for i in graph.initializer}
    nodes=[]
    count=0
    for node in graph.node:
        if node.op_type!="ConvInteger":
            nodes.append(node)
            continue
        count+=1
        tag=f"cherry_compat_{count}"
        x,w=node.input[:2]
        weight=numpy_helper.to_array(init[w]).astype(np.float32)
        if len(node.input)>3 and node.input[3]:
            zp=numpy_helper.to_array(init[node.input[3]]).astype(np.float32)
            if zp.size>1:zp=zp.reshape((-1,)+(1,)*(weight.ndim-1))
            weight=weight-zp
        weight_name=tag+"_weight"
        graph.initializer.append(numpy_helper.from_array(weight,weight_name))
        nodes.append(helper.make_node("Cast",[x],[tag+"_x"],to=TensorProto.FLOAT))
        centered=tag+"_x"
        if len(node.input)>2 and node.input[2]:
            nodes.append(helper.make_node("Cast",[node.input[2]],[tag+"_zp"],to=TensorProto.FLOAT))
            centered=tag+"_centered"
            nodes.append(helper.make_node("Sub",[tag+"_x",tag+"_zp"],[centered]))
        conv=helper.make_node("Conv",[centered,weight_name],[tag+"_acc"])
        conv.attribute.extend(node.attribute)
        nodes.append(conv)
        nodes.append(helper.make_node("Cast",[tag+"_acc"],list(node.output),to=TensorProto.INT32))
    assert count>0,"Expected unsupported ConvInteger nodes"
    del graph.node[:]
    graph.node.extend(nodes)
    onnx.checker.check_model(model)
    result=model.SerializeToString()
    session=ort.InferenceSession(result,providers=["CPUExecutionProvider"])
    for width in (64,128,256):
        x=np.ones((1,1,32,width),dtype=np.float32)
        x[:,:,8:24,12:20]=0
        y=session.run(None,{session.get_inputs()[0].name:x})[0]
        assert y.ndim==3 and y.shape[-1]==272 and np.isfinite(y).all(),y.shape
    print("CRNN compatibility smoke passed; rewritten ConvInteger nodes:",count)
    return result
