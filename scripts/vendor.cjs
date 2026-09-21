const fs=require('fs');
const dir='app/src/main/assets/vendor'; fs.mkdirSync(dir,{recursive:true});
for(const name of ['libsodium','libsodium-wrappers']) {
 fs.copyFileSync(require.resolve(name),`${dir}/${name}.js`);
 fs.copyFileSync(`node_modules/${name}/LICENSE`,`${dir}/${name}.LICENSE`);
}
